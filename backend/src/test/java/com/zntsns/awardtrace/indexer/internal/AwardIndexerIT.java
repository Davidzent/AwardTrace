package com.zntsns.awardtrace.indexer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.zntsns.awardtrace.AwardRows;
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
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void indexesTheCurrentAwardRowWhenItsChangeEventArrives() throws Exception {
        String awardId = "CONT_AWD_INDEXER_EVENT";
        AwardRows.saveAward(jdbc, awardId, 3, "55000.00", false);

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
    void countsAndTotalsTheAwardsReportedSubawards() throws Exception {
        String awardId = "CONT_AWD_INDEXER_SUBAWARDS";
        AwardRows.saveAward(jdbc, awardId, 1, "55000.00", false);
        AwardRows.saveAward(jdbc, "CONT_AWD_INDEXER_NO_SUBAWARDS", 1, "10.00", false);
        for (String[] subaward : new String[][] {{"S1", "1000.00"}, {"S2", "-200.50"}}) {
            jdbc.sql("""
                    INSERT INTO subaward (subaward_key, prime_award_id, prime_recipient_uei, prime_recipient_name,
                                          sub_recipient_uei, sub_recipient_name, amount, action_date,
                                          source_modified_at)
                    VALUES (:key, :awardId, 'MN5KRX2W9R46', 'NOMADIC LAND CAMPS, LLC', 'E2QCEKQXLN48', 'DVORAK, LLC',
                            :amount, DATE '2026-08-01', now())
                    """)
                    .param("key", subaward[0])
                    .param("awardId", awardId)
                    .param("amount", new BigDecimal(subaward[1]))
                    .update();
        }

        indexer.index(List.of(awardId, "CONT_AWD_INDEXER_NO_SUBAWARDS"));

        var withSubawards = document(awardId).source();
        assertThat(withSubawards).containsEntry("subaward_count", 2);
        assertThat(((Number) withSubawards.get("subaward_total")).doubleValue()).isEqualTo(799.5);
        var without = document("CONT_AWD_INDEXER_NO_SUBAWARDS").source();
        assertThat(without).containsEntry("subaward_count", 0);
        assertThat(((Number) without.get("subaward_total")).doubleValue()).isZero();
    }

    @Test
    void keepsTheNewerDocumentWhenAnOlderVersionIsWritten() throws Exception {
        String awardId = "CONT_AWD_INDEXER_VERSIONS";
        AwardRows.saveAward(jdbc, awardId, 3, "55000.00", false);
        assertThat(indexer.index(List.of(awardId))).isEqualTo(new Result(1, 0, 0, 0));

        // A stale read: the row an indexer saw before the version-3 change committed.
        AwardRows.saveAward(jdbc, awardId, 2, "1.00", false);

        assertThat(indexer.index(List.of(awardId))).isEqualTo(new Result(0, 0, 1, 0));
        assertThat(document(awardId).version()).isEqualTo(3);
        assertThat(((Number) document(awardId).source().get("total_obligated")).doubleValue()).isEqualTo(55000.0);
    }

    @Test
    void deletesTheDocumentOfADeletedAward() throws Exception {
        String awardId = "CONT_AWD_INDEXER_DELETED";
        AwardRows.saveAward(jdbc, awardId, 1, "27500.00", false);
        indexer.index(List.of(awardId));

        AwardRows.saveAward(jdbc, awardId, 2, "27500.00", true);

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
}
