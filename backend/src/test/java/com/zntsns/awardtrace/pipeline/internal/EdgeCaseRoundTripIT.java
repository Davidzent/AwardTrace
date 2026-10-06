package com.zntsns.awardtrace.pipeline.internal;

import static com.zntsns.awardtrace.pipeline.internal.TestEvents.file;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.SearchIndexes;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The hand-made edge cases in doc 12's fixture list, each sent as transaction events and followed through PostgreSQL,
 * the index, and the API, as {@link MoneyRoundTripIT} does with amounts. It shares that test's Spring context, so it
 * asserts on its own awards only, and empties the tables and the index after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"pipeline", "indexer", "api"})
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class EdgeCaseRoundTripIT {

    private static final String SEPT_9 = file("20260909");

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ElasticsearchClient elasticsearch;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @AfterEach
    void emptyTablesAndIndex() throws Exception {
        AwardRows.emptyTables(jdbc);
        elasticsearch.deleteByQuery(delete -> delete.index(SearchIndexes.AWARDS)
                .query(query -> query.matchAll(all -> all))
                .refresh(true));
    }

    @Test
    void keepsAnAwardWhoseDeobligationTakesItsTotalBelowZero() throws Exception {
        String awardId = "CONT_AWD_EDGE_DEOBLIGATED";
        send(transaction(awardId, "0", "2026-07-16", "10000.00", "10000.00", SEPT_9),
                transaction(awardId, "P00001", "2026-08-24", "-15000.00", "-5000.00", SEPT_9));
        awaitIndexed(awardId, "-5000.00");

        assertThat(storedTotal(awardId)).isEqualByComparingTo("-5000.00");
        JsonNode detail = json("/api/v1/awards/" + awardId);
        assertThat(detail.get("total_obligated").asString()).isEqualTo("-5000.00");
        assertThat(obligations(detail)).containsExactly("P00001 -15000.00", "0 10000.00");
        assertThat(awardIds("/api/v1/awards/search?max_amount=-0.01")).contains(awardId);
    }

    @Test
    void keepsAZeroDollarAdministrativeModificationAsTheLatestAction() throws Exception {
        String awardId = "CONT_AWD_EDGE_ADMIN_MOD";
        send(transaction(awardId, "0", "2026-07-16", "5000.00", "5000.00", SEPT_9),
                transaction(awardId, "P00001", "2026-09-02", "0.00", "5000.00", SEPT_9));
        await().atMost(Duration.ofSeconds(60)).until(() -> jdbc.sql(
                "SELECT count(*) FROM award_transaction WHERE award_id = ?").param(awardId).query(Long.class).single() == 2);
        awaitIndexed(awardId, "5000.00");

        assertThat(jdbc.sql("SELECT last_action_date::text FROM award WHERE award_id = ?").param(awardId)
                .query(String.class).single()).isEqualTo("2026-09-02");
        JsonNode detail = json("/api/v1/awards/" + awardId);
        assertThat(detail.get("total_obligated").asString()).isEqualTo("5000.00");
        assertThat(obligations(detail)).containsExactly("P00001 0.00", "0 5000.00");
    }

    @Test
    void searchesAVagueDescriptionAndKeepsItsProductCodeCategory() throws Exception {
        String awardId = "CONT_AWD_EDGE_SEE_SCHEDULE";
        send(transaction(awardId, "0", "2026-07-16", "2500.00", "2500.00", SEPT_9, "MN5KRX2W9R46",
                "NOMADIC LAND CAMPS, LLC", "SEE SCHEDULE"));
        awaitIndexed(awardId, "2500.00");

        JsonNode detail = json("/api/v1/awards/" + awardId);
        assertThat(detail.get("description").asString()).isEqualTo("SEE SCHEDULE");
        // PSC F003 is in group F; the classifier, were it running, would call this vague and leave it the same.
        assertThat(detail.at("/category/code").asString()).isEqualTo("NATURAL_RESOURCES");
        assertThat(detail.at("/category/source").asString()).isEqualTo("baseline");
        assertThat(awardIds("/api/v1/awards/search?q=schedule")).contains(awardId);
    }

    @Test
    void keepsA4000CharacterDescriptionWholeAndHighlightsOnlyAFragment() throws Exception {
        String awardId = "CONT_AWD_EDGE_LONG_DESCRIPTION";
        String sentence = "TYPE 6 WILDLAND FIRE ENGINE WITH CREW AND WATER TENDER SUPPORT FOR THE NORTHERN REGION. ";
        String end = " PALOMINO";
        String description = sentence.repeat(4000 / sentence.length() + 1).substring(0, 4000 - end.length()) + end;
        assertThat(description).hasSize(4000);
        send(transaction(awardId, "0", "2026-07-16", "750000.00", "750000.00", SEPT_9, "MN5KRX2W9R46",
                "NOMADIC LAND CAMPS, LLC", description));
        awaitIndexed(awardId, "750000.00");

        assertThat(jdbc.sql("SELECT description FROM award WHERE award_id = ?").param(awardId).query(String.class)
                .single()).isEqualTo(description);
        assertThat(json("/api/v1/awards/" + awardId).get("description").asString()).isEqualTo(description);

        // The word at the very end is found, and a reader gets one short fragment around it, not 4,000 characters.
        JsonNode result = json("/api/v1/awards/search?q=palomino").at("/results/0");
        assertThat(result.get("award_id").asString()).isEqualTo(awardId);
        assertThat(result.get("description").asString()).hasSize(4000);
        assertThat(result.get("description_highlight").asString())
                .contains("<mark>PALOMINO</mark>")
                .hasSizeLessThan(250);
    }

    @Test
    void keepsNonAsciiCharactersInARecipientNameExact() throws Exception {
        String awardId = "CONT_AWD_EDGE_NON_ASCII";
        String uei = "MULLERFILS01";
        String name = "MÜLLER & FILS SOCIÉTÉ FORESTIÈRE, S.À R.L.";
        send(transaction(awardId, "0", "2026-07-16", "1200.00", "1200.00", SEPT_9, uei, name, "TREE PLANTING"));
        awaitIndexed(awardId, "1200.00");

        assertThat(jdbc.sql("SELECT name FROM recipient WHERE uei = ?").param(uei).query(String.class).single())
                .isEqualTo(name);
        assertThat(json("/api/v1/awards/" + awardId).at("/recipient/name").asString()).isEqualTo(name);
        assertThat(json("/api/v1/recipients/" + uei).get("name").asString()).isEqualTo(name);
        assertThat(json("/api/v1/awards/search?q=müller").at("/results/0/recipient/name").asString()).isEqualTo(name);
        assertThat(json("/api/v1/recipients/suggest?q=société").at("/0/uei").asString()).isEqualTo(uei);
    }

    private void send(ContractTransactionIngested... events) throws Exception {
        for (var event : events) {
            kafka.send(KafkaTopics.AWARD_TRANSACTIONS, event.awardId(), EventCodec.write(EventEnvelope.of(event, null)))
                    .get();
        }
    }

    /** Waits until the index holds the award at its final total, after the pipeline, the relay, and the indexer. */
    @SuppressWarnings("rawtypes")
    private void awaitIndexed(String awardId, String total) throws Exception {
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            var document = elasticsearch.get(get -> get.index(SearchIndexes.AWARDS).id(awardId), Map.class);
            return document.found()
                    && new BigDecimal(String.valueOf(document.source().get("total_obligated")))
                            .compareTo(new BigDecimal(total)) == 0;
        });
        elasticsearch.indices().refresh(refresh -> refresh.index(SearchIndexes.AWARDS));
    }

    private BigDecimal storedTotal(String awardId) {
        return jdbc.sql("SELECT total_obligated FROM award WHERE award_id = ?").param(awardId)
                .query(BigDecimal.class).single();
    }

    /** Each modification as "number amount", newest first, as the detail lists them. */
    private static List<String> obligations(JsonNode detail) {
        return detail.get("transactions").valueStream()
                .map(transaction -> transaction.get("modification_number").asString() + " "
                        + transaction.get("federal_action_obligation").asString())
                .toList();
    }

    private JsonNode json(String uri) throws Exception {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
    }

    private List<String> awardIds(String uri) throws Exception {
        return json(uri).get("results").valueStream().map(result -> result.get("award_id").asString()).toList();
    }
}
