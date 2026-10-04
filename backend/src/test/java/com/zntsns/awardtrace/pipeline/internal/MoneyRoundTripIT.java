package com.zntsns.awardtrace.pipeline.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.SearchIndexes;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * Money takes four forms on its way to a reader: a decimal string in the event, numeric(18,2) in PostgreSQL, a
 * scaled_float with an exact _source in Elasticsearch, and a decimal string in the API. Every amount must leave each
 * one exactly as it entered (doc 12). The largest is just under $100 billion, past any single federal award.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"pipeline", "indexer", "api"})
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
class MoneyRoundTripIT {

    /** Each award has one transaction, which obligates its whole total. */
    private static final Map<String, String> AMOUNTS = new LinkedHashMap<>();

    static {
        AMOUNTS.put("CONT_AWD_MONEY_LARGEST", "99999999999.99");
        AMOUNTS.put("CONT_AWD_MONEY_ODD_CENTS", "1234567.89");
        AMOUNTS.put("CONT_AWD_MONEY_CENT", "0.01");
        AMOUNTS.put("CONT_AWD_MONEY_MINUS_CENT", "-0.01");
        AMOUNTS.put("CONT_AWD_MONEY_DEOBLIGATED", "-99999999999.99");
    }

    private static final String SUM = "1234567.89";

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ElasticsearchClient elasticsearch;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Test
    void keepsEveryAmountExactFromTheEventToTheApi() throws Exception {
        for (var amount : AMOUNTS.entrySet()) {
            var event = TestEvents.transaction(amount.getKey(), "0", "2026-07-16", amount.getValue(), amount.getValue(),
                    "FY2026_012_Contracts_Full_20260909_1.csv");
            kafka.send(KafkaTopics.AWARD_TRANSACTIONS, event.awardId(), EventCodec.write(EventEnvelope.of(event, null)))
                    .get();
        }
        // The pipeline writes each award, the relay announces it, and the indexer indexes it.
        await().atMost(Duration.ofSeconds(60)).until(() -> {
            elasticsearch.indices().refresh(request -> request.index(SearchIndexes.AWARDS));
            return elasticsearch.count(request -> request.index(SearchIndexes.AWARDS)).count() == AMOUNTS.size();
        });

        // PostgreSQL
        Map<String, BigDecimal> stored = jdbc.sql("SELECT award_id, total_obligated FROM award")
                .query((row, n) -> Map.entry(row.getString(1), row.getBigDecimal(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        AMOUNTS.forEach((awardId, amount) -> assertThat(stored.get(awardId)).isEqualTo(new BigDecimal(amount)));

        // The award detail, read from PostgreSQL
        for (var amount : AMOUNTS.entrySet()) {
            JsonNode detail = json("/api/v1/awards/" + amount.getKey());
            assertThat(detail.get("total_obligated").asString()).isEqualTo(amount.getValue());
            assertThat(detail.get("potential_total_value").asString()).isEqualTo(amount.getValue());
            assertThat(detail.at("/transactions/0/federal_action_obligation").asString()).isEqualTo(amount.getValue());
        }

        // Search, read from Elasticsearch: the _source amounts, the sum over every match, and the sort
        JsonNode largestFirst = json("/api/v1/awards/search?sort=largest");
        assertThat(largestFirst.get("total_obligated").asString()).isEqualTo(SUM);
        assertThat(largestFirst.get("results").valueStream().map(result -> result.get("award_id").asString()))
                .containsExactlyElementsOf(AMOUNTS.keySet());
        largestFirst.get("results").forEach(result -> assertThat(result.get("total_obligated").asString())
                .isEqualTo(AMOUNTS.get(result.get("award_id").asString())));

        // Amount filters run on the index's scaled values, so a cent either side of zero must land correctly.
        assertThat(awardIds("/api/v1/awards/search?min_amount=0.01"))
                .containsExactlyInAnyOrder("CONT_AWD_MONEY_LARGEST", "CONT_AWD_MONEY_ODD_CENTS", "CONT_AWD_MONEY_CENT");
        assertThat(awardIds("/api/v1/awards/search?max_amount=-0.01"))
                .containsExactlyInAnyOrder("CONT_AWD_MONEY_MINUS_CENT", "CONT_AWD_MONEY_DEOBLIGATED");

        // The recipient rollup, summed by PostgreSQL
        assertThat(json("/api/v1/recipients/MN5KRX2W9R46").at("/totals/total_obligated").asString()).isEqualTo(SUM);
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
