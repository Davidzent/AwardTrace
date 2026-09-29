package com.zntsns.awardtrace.search.internal;

import static com.zntsns.awardtrace.AwardRows.saveSearchableAward;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.SearchIndexes;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Seeds awards once and indexes them the way production does: award-changed events through the indexer. The
 * indexer and the API run together, as they do in the single production JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "indexer"})
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchControllerIT {

    private static final List<String> AWARDS = List.of("CONT_AWD_SEARCH_HELICOPTER", "CONT_AWD_SEARCH_CAMPS",
            "CONT_AWD_SEARCH_SOFTWARE");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    ElasticsearchClient elasticsearch;

    @BeforeAll
    void indexAwards() throws Exception {
        AwardRows.emptyTables(jdbc);
        saveSearchableAward(jdbc, AWARDS.get(0), "12024B26C0001", "Helicopter services for wildfire suppression",
                "AAAAAAAAAAA1", "SKYLINE AVIATION INC", "ID", "4812000.00", "2026-08-01");
        saveSearchableAward(jdbc, AWARDS.get(1), "12024B26M0522", "Nomadic land camps for fire crews",
                "MN5KRX2W9R46", "NOMADIC LAND CAMPS, LLC", "ID", "55000.00", "2026-08-24");
        saveSearchableAward(jdbc, AWARDS.get(2), "12318726F0042", "Cloud software licenses",
                "BBBBBBBBBBB2", "ACME FEDERAL LLC", "VA", "950.00", "2025-11-03");
        for (String awardId : AWARDS) {
            var event = new AwardChanged(awardId, 1, "TRANSACTION");
            kafka.send(KafkaTopics.AWARDS_CHANGED, awardId, EventCodec.write(EventEnvelope.of(event, null))).get();
        }
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            elasticsearch.indices().refresh(request -> request.index(SearchIndexes.AWARDS));
            return elasticsearch.count(request -> request.index(SearchIndexes.AWARDS)).count() == AWARDS.size();
        });
    }

    @Test
    void findsAwardsByKeywordAndHighlightsTheMatch() {
        var result = mvc.get().uri("/api/v1/awards/search?q=helicopter").exchange();

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(1);
        json.extractingPath("$.results[0].award_id").isEqualTo(AWARDS.get(0));
        json.extractingPath("$.results[0].description_highlight")
                .isEqualTo("<mark>Helicopter</mark> services for wildfire suppression");
        json.extractingPath("$.results[0].recipient.name").isEqualTo("SKYLINE AVIATION INC");
        json.extractingPath("$.results[0].agency.subtier_name").isEqualTo("Forest Service");
        json.extractingPath("$.results[0].total_obligated").isEqualTo("4812000.00");
    }

    @Test
    void putsAPastedContractNumberFirst() {
        var result = mvc.get().uri("/api/v1/awards/search?q=12024b26m0522").exchange();

        assertThat(result).bodyJson().extractingPath("$.results[0].award_id").isEqualTo(AWARDS.get(1));
    }

    @Test
    void listsNewestFirstWithoutAKeywordAndSumsEveryMatch() {
        var result = mvc.get().uri("/api/v1/awards/search").exchange();

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(3);
        json.extractingPath("$.results[*].award_id").asArray()
                .containsExactly(AWARDS.get(1), AWARDS.get(0), AWARDS.get(2));
        json.extractingPath("$.total_obligated").isEqualTo("4867950.00");
    }

    @Test
    void filtersAndSortsLargestFirstOnePageAtATime() {
        var result = mvc.get()
                .uri("/api/v1/awards/search?state=ID&state=VA&min_amount=1000&sort=largest&size=1&page=2")
                .exchange();

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(2);
        json.extractingPath("$.total_obligated").isEqualTo("4867000.00");
        json.extractingPath("$.page").isEqualTo(2);
        json.extractingPath("$.results[*].award_id").asArray().containsExactly(AWARDS.get(1));
    }

    @Test
    void reportsEveryInvalidParameterAtOnce() {
        var result = mvc.get()
                .uri("/api/v1/awards/search?page=abc&size=0&fiscal_year=abc&min_amount=500&max_amount=100&sort=cheapest")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.type").isEqualTo("invalid-search-parameters");
        json.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("page", "size", "fiscal_year", "max_amount", "sort");
    }

    @Test
    void refusesPagesPastTheResultWindow() {
        var result = mvc.get().uri("/api/v1/awards/search?page=501&size=20").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("page");
    }
}
