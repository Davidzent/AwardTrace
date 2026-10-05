package com.zntsns.awardtrace.ingest.internal;

import static com.zntsns.awardtrace.ingest.internal.Fixtures.DELTA_FILE;
import static com.zntsns.awardtrace.ingest.internal.Fixtures.FULL_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.enrichment.ClassificationSnapshots;
import com.zntsns.awardtrace.ingest.internal.IngestRuns.Mode;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.SearchIndexes;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * PostgreSQL and the index are projections of the files in S3 (ADR 0002), so wiping both and replaying from S3 alone
 * must rebuild them exactly (doc 12). The rebuild drill in miniature, with every role in one context.
 */
@SpringBootTest
@ActiveProfiles({"ingest", "pipeline", "indexer"})
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class ReplayRebuildIT {

    private static final FakeUsaspending USASPENDING = new FakeUsaspending()
            .put("FY2026_012_Contracts_Full_20260906.zip", Fixtures.zip(FULL_FILE, Fixtures.csv(FULL_FILE)))
            .put("FY(All)_012_Contracts_Delta_20260906.zip", Fixtures.zip(DELTA_FILE, Fixtures.csv(DELTA_FILE)));

    /**
     * Each table as a key and a row, minus what a reload legitimately changes: versions and timestamps count and date
     * changes, which differ with batching. Whether a row is deleted must not differ, so that stays.
     */
    private static final Map<String, String> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("agency", "SELECT code AS k, to_jsonb(t) AS row FROM agency t");
        TABLES.put("recipient", "SELECT uei AS k, to_jsonb(t) AS row FROM recipient t");
        TABLES.put("award", """
                SELECT award_id AS k, (to_jsonb(t) - '{index_version,created_at,updated_at,deleted_at}'::text[])
                                      || jsonb_build_object('deleted', deleted_at IS NOT NULL) AS row
                FROM award t""");
        TABLES.put("award_transaction", """
                SELECT transaction_id AS k, (to_jsonb(t) - '{ingested_at,deleted_at}'::text[])
                                            || jsonb_build_object('deleted', deleted_at IS NOT NULL) AS row
                FROM award_transaction t""");
        TABLES.put("subaward", "SELECT subaward_key AS k, to_jsonb(t) - 'ingested_at' AS row FROM subaward t");
        TABLES.put("recipient_edge", "SELECT prime_uei || sub_uei AS k, to_jsonb(t) AS row FROM recipient_edge t");
        TABLES.put("classification", "SELECT description_hash AS k, to_jsonb(t) AS row FROM classification t");
    }

    @SuppressWarnings("rawtypes")
    private record Snapshot(Map<String, String> tables, List<Map> documents) {
    }

    @DynamicPropertySource
    static void usaspending(DynamicPropertyRegistry registry) {
        registry.add("awardtrace.ingest.archive-url", USASPENDING::url);
        registry.add("awardtrace.ingest.api-url", USASPENDING::apiUrl);
        registry.add("awardtrace.ingest.download-poll-interval", () -> "10ms");
        registry.add("awardtrace.ingest.agencies", () -> "012");
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    IngestRuns runs;

    @Autowired
    ClassificationSnapshots classifications;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ElasticsearchClient elasticsearch;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    ApplicationContext context;

    @AfterAll
    static void stopUsaspending() {
        USASPENDING.stop();
    }

    @Test
    void rebuildsPostgresqlAndTheIndexFromS3Alone() throws Exception {
        runs.run(Mode.BACKFILL);
        runs.run(Mode.DELTA);
        classifyEveryDescription();
        Snapshot loaded = snapshotWhenQuiet();
        // Something to compare in every table and in the index.
        assertThat(loaded.tables().values()).noneMatch(table -> table.startsWith("0 rows"));
        assertThat(loaded.documents()).isNotEmpty();

        wipe();
        runs.run(Mode.REPLAY);

        assertThat(snapshotWhenQuiet()).isEqualTo(loaded);
    }

    /**
     * What an enrichment run leaves: a category for each description, a new version and an event for each award, so the
     * index shows the classifier's category, and a snapshot of them in S3. Classifications can't be rebuilt from the
     * source files, so the replay must restore them from the snapshot before it rebuilds the awards (doc 09).
     */
    private void classifyEveryDescription() throws IOException {
        jdbc.sql("""
                INSERT INTO classification (description_hash, category, confidence, model, prompt_version)
                SELECT DISTINCT description_hash, 'NATURAL_RESOURCES', 0.87, 'claude-haiku-4-5', 'v1'
                FROM award WHERE description_hash IS NOT NULL
                """).update();
        jdbc.sql("""
                WITH changed AS (
                    UPDATE award SET index_version = index_version + 1, updated_at = now()
                    WHERE description_hash IS NOT NULL AND deleted_at IS NULL
                    RETURNING award_id, index_version
                )
                INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version)
                SELECT award_id, 'AwardChanged', 'CLASSIFICATION', index_version FROM changed
                """).update();
        classifications.write();
    }

    /** Every event consumed, every outbox row relayed, and every relayed change indexed. */
    private Snapshot snapshotWhenQuiet() throws IOException {
        try (var admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            await().atMost(Duration.ofSeconds(60)).until(() ->
                    lag(admin, KafkaTopics.PIPELINE_GROUP, KafkaTopics.AWARD_TRANSACTIONS) == 0
                            && lag(admin, KafkaTopics.SUBAWARD_GROUP, KafkaTopics.SUBAWARDS) == 0
                            && jdbc.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Long.class)
                                    .single() == 0
                            && lag(admin, KafkaTopics.INDEXER_GROUP, KafkaTopics.AWARDS_CHANGED) == 0);
        }
        elasticsearch.indices().refresh(request -> request.index(SearchIndexes.AWARDS));

        var tables = new LinkedHashMap<String, String>();
        TABLES.forEach((table, rows) -> tables.put(table, jdbc.sql("""
                SELECT count(*) || ' rows ' || coalesce(md5(string_agg(row::text, ',' ORDER BY k)), '')
                FROM (%s) s
                """.formatted(rows)).query(String.class).single()));
        @SuppressWarnings("rawtypes")
        List<Map> documents = elasticsearch.search(request -> request
                        .index(SearchIndexes.AWARDS)
                        .size(1_000)
                        .sort(sort -> sort.field(field -> field.field("award_id"))), Map.class)
                .hits().hits().stream()
                .map(Hit::source)
                .toList();
        return new Snapshot(tables, documents);
    }

    /** What a lost host looks like: an empty database, and an empty cluster in which the indexer creates the index. */
    private void wipe() throws Exception {
        jdbc.sql("""
                TRUNCATE award_transaction, award, recipient, agency, outbox, subaward, classification, ingest_file,
                         ingest_run
                """).update();
        jdbc.sql("REFRESH MATERIALIZED VIEW recipient_edge").update();
        var indices = elasticsearch.indices().getAlias(request -> request.name(SearchIndexes.AWARDS)).aliases().keySet();
        elasticsearch.indices().delete(request -> request.index(List.copyOf(indices)));
        ((InitializingBean) context.getBean("awardsIndex")).afterPropertiesSet();
    }

    private static long lag(Admin admin, String group, String topic) throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetAndMetadata> committed =
                admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get();
        var latest = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic).partitions().stream()
                .collect(Collectors.toMap(partition -> new TopicPartition(topic, partition.partition()),
                        partition -> OffsetSpec.latest()));
        long lag = 0;
        for (var end : admin.listOffsets(latest).all().get().entrySet()) {
            OffsetAndMetadata done = committed.get(end.getKey());
            lag += end.getValue().offset() - (done == null ? 0 : done.offset());
        }
        return lag;
    }
}
