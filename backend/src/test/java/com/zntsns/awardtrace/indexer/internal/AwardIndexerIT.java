package com.zntsns.awardtrace.indexer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.indexer.internal.AwardIndexer.Result;
import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("indexer")
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class AwardIndexerIT {

    @Autowired
    AwardIndexer indexer;

    @Autowired
    ElasticsearchClient elasticsearch;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        jdbc.sql("TRUNCATE award_transaction, award, recipient, agency").update();
    }

    @Test
    void indexesTheCurrentAwardRowWhenItsChangeEventArrives() throws Exception {
        String awardId = "CONT_AWD_INDEXER_EVENT";
        saveAward(awardId, 3, "55000.00", false);

        var event = new AwardChanged(awardId, 3, "TRANSACTION");
        kafka.send(KafkaTopics.AWARDS_CHANGED, awardId, EventCodec.write(EventEnvelope.of(event, null))).get();

        await().atMost(Duration.ofSeconds(20)).until(() -> document(awardId).found());
        GetResponse<Map> document = document(awardId);
        assertThat(document.version()).isEqualTo(3);
        assertThat(document.source())
                .containsEntry("award_id", awardId)
                .containsEntry("recipient_name", "NOMADIC LAND CAMPS, LLC")
                .containsEntry("awarding_toptier_name", "Department of Agriculture")
                .containsEntry("awarding_subtier_name", "Forest Service")
                .containsEntry("last_action_date", "2026-08-24")
                .containsEntry("fiscal_year", 2026)
                .doesNotContainKeys("index_version", "deleted");
        assertThat(((Number) document.source().get("total_obligated")).doubleValue()).isEqualTo(55000.0);
    }

    @Test
    void keepsTheNewerDocumentWhenAnOlderVersionIsWritten() throws Exception {
        String awardId = "CONT_AWD_INDEXER_VERSIONS";
        saveAward(awardId, 3, "55000.00", false);
        assertThat(indexer.index(List.of(awardId))).isEqualTo(new Result(1, 0, 0, 0));

        // A stale read: the row an indexer saw before the version-3 change committed.
        saveAward(awardId, 2, "1.00", false);

        assertThat(indexer.index(List.of(awardId))).isEqualTo(new Result(0, 0, 1, 0));
        assertThat(document(awardId).version()).isEqualTo(3);
        assertThat(((Number) document(awardId).source().get("total_obligated")).doubleValue()).isEqualTo(55000.0);
    }

    @Test
    void deletesTheDocumentOfADeletedAward() throws Exception {
        String awardId = "CONT_AWD_INDEXER_DELETED";
        saveAward(awardId, 1, "27500.00", false);
        indexer.index(List.of(awardId));

        saveAward(awardId, 2, "27500.00", true);

        assertThat(indexer.index(List.of(awardId))).isEqualTo(new Result(0, 1, 0, 0));
        assertThat(document(awardId).found()).isFalse();
    }

    @Test
    void skipsAnAwardWithNoRow() throws Exception {
        assertThat(indexer.index(List.of("CONT_AWD_INDEXER_NONE"))).isEqualTo(new Result(0, 0, 0, 1));
    }

    @SuppressWarnings("rawtypes")
    private GetResponse<Map> document(String awardId) throws IOException {
        return elasticsearch.get(request -> request.index(AwardsIndex.ALIAS).id(awardId), Map.class);
    }

    private void saveAward(String awardId, long indexVersion, String totalObligated, boolean deleted) {
        jdbc.sql("""
                INSERT INTO agency (code, level, name) VALUES ('012', 'toptier', 'Department of Agriculture')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO agency (code, level, name, parent_code) VALUES ('12C2', 'subtier', 'Forest Service', '012')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO recipient (uei, name) VALUES ('MN5KRX2W9R46', 'NOMADIC LAND CAMPS, LLC')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO award (award_id, piid, award_type, description, awarding_toptier_code,
                                   awarding_subtier_code, recipient_uei, naics_code, psc_code, pop_state_code,
                                   total_obligated, first_action_date, last_action_date, source_modified_at,
                                   index_version, transaction_count, deleted_at)
                VALUES (:awardId, '12024B26M0522', 'C', 'NOMADIC LAND CAMPS, LLC IDIPF000347 E40', '012', '12C2',
                        'MN5KRX2W9R46', '517810', 'F003', 'ID', :total, DATE '2026-07-16', DATE '2026-08-24', now(),
                        :indexVersion, 3, CASE WHEN :deleted THEN now() END)
                ON CONFLICT (award_id) DO UPDATE SET total_obligated = EXCLUDED.total_obligated,
                    index_version = EXCLUDED.index_version, deleted_at = EXCLUDED.deleted_at
                """)
                .param("awardId", awardId)
                .param("total", new BigDecimal(totalObligated))
                .param("indexVersion", indexVersion)
                .param("deleted", deleted)
                .update();
    }
}
