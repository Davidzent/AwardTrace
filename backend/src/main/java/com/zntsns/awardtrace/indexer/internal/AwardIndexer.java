package com.zntsns.awardtrace.indexer.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.KafkaTopics;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Turns award-changed events into Elasticsearch documents. Each document is built from the current PostgreSQL row,
 * never from the event, and written with {@code index_version} as its external version, so Elasticsearch rejects any
 * write older than the one it holds. A deleted award's document is deleted the same way.
 */
@Component
@Profile("indexer")
class AwardIndexer {

    private static final Logger log = LoggerFactory.getLogger(AwardIndexer.class);

    record Result(int indexed, int deleted, int conflicts, int missing) {
    }

    // Column names are the mapping's field names; index_version and deleted steer the write and aren't indexed.
    private static final String DOCUMENTS = """
            SELECT a.award_id, a.piid, a.description, a.recipient_uei, r.name AS recipient_name, r.parent_uei,
                   a.awarding_toptier_code, toptier.name AS awarding_toptier_name,
                   subtier.name AS awarding_subtier_name, a.naics_code, a.naics_description, a.psc_code,
                   a.pop_state_code, a.total_obligated, a.potential_total_value, a.last_action_date,
                   a.pop_start_date, a.pop_end_date, a.fiscal_year,
                   s.subaward_count, coalesce(s.subaward_total, 0) AS subaward_total,
                   a.index_version, a.deleted_at IS NOT NULL AS deleted
            FROM award a
            JOIN recipient r ON r.uei = a.recipient_uei
            JOIN agency toptier ON toptier.code = a.awarding_toptier_code
            LEFT JOIN agency subtier ON subtier.code = a.awarding_subtier_code
            -- The award's reported subawards (ADR 0014); an award without any gets 0 and 0.
            CROSS JOIN LATERAL (
                SELECT count(*) AS subaward_count, sum(amount) AS subaward_total
                FROM subaward WHERE prime_award_id = a.award_id
            ) s
            WHERE a.award_id IN (:awardIds)
            """;

    private final JdbcClient jdbc;
    private final ElasticsearchClient elasticsearch;

    AwardIndexer(JdbcClient jdbc, ElasticsearchClient elasticsearch) {
        this.jdbc = jdbc;
        this.elasticsearch = elasticsearch;
    }

    @KafkaListener(id = KafkaTopics.INDEXER_GROUP, topics = KafkaTopics.AWARDS_CHANGED, batch = "true")
    void onBatch(List<ConsumerRecord<String, String>> records) throws IOException {
        var awardIds = new LinkedHashSet<String>();
        for (var record : records) {
            try {
                awardIds.add(EventCodec.read(record.value(), AwardChanged.class).payload().awardId());
            } catch (JacksonException e) {
                // The outbox relay writes these events, so an unreadable one is a bug to fix, not data to retry.
                log.error("Skipping unreadable award-changed event at offset {}: {}", record.offset(), record.value(), e);
            }
        }
        index(awardIds);
    }

    /** Several events for one award in a batch become one write of its current row. */
    Result index(Collection<String> awardIds) throws IOException {
        return index(awardIds, AwardsIndex.ALIAS);
    }

    /** Writes to {@code target}: the alias normally, or a new index while a rebuild fills it. */
    Result index(Collection<String> awardIds, String target) throws IOException {
        if (awardIds.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        List<Map<String, Object>> rows = jdbc.sql(DOCUMENTS).param("awardIds", awardIds).query().listOfRows();
        var operations = new ArrayList<BulkOperation>();
        for (Map<String, Object> row : rows) {
            String awardId = (String) row.get("award_id");
            long version = ((Number) row.remove("index_version")).longValue();
            if ((Boolean) row.remove("deleted")) {
                operations.add(BulkOperation.of(bulk -> bulk.delete(delete -> delete
                        .index(target).id(awardId).version(version).versionType(VersionType.External))));
            } else {
                Map<String, Object> document = document(row);
                operations.add(BulkOperation.of(bulk -> bulk.index(index -> index
                        .index(target).id(awardId).version(version).versionType(VersionType.External)
                        .document(document))));
            }
        }
        if (operations.isEmpty()) {
            return new Result(0, 0, 0, awardIds.size());
        }

        int indexed = 0;
        int deleted = 0;
        int conflicts = 0;
        var failures = new ArrayList<String>();
        for (var item : elasticsearch.bulk(bulk -> bulk.operations(operations)).items()) {
            if (item.status() == 409) {
                conflicts++; // A newer version is already indexed, which is the outcome we want.
            } else if (item.error() != null) {
                failures.add(item.id() + ": " + item.error().reason());
            } else if (item.operationType() == OperationType.Delete) {
                deleted++; // Including a document that was already gone.
            } else {
                indexed++;
            }
        }
        if (!failures.isEmpty()) {
            // Retried with the whole batch; writes that did succeed are rejected as conflicts next time.
            throw new IOException("Elasticsearch rejected " + failures.size() + " award writes: " + failures);
        }
        return new Result(indexed, deleted, conflicts, awardIds.size() - rows.size());
    }

    /** Only strings, numbers, and ISO dates, so the result doesn't depend on the client's JSON mapper. */
    private static Map<String, Object> document(Map<String, Object> row) {
        var document = new LinkedHashMap<String, Object>();
        row.forEach((field, value) -> {
            if (value != null) {
                document.put(field, value instanceof Date date ? date.toLocalDate().toString() : value);
            }
        });
        return document;
    }
}
