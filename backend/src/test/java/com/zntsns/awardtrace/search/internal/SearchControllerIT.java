package com.zntsns.awardtrace.search.internal;

import static com.zntsns.awardtrace.AwardRows.saveSearchableAward;
import static com.zntsns.awardtrace.AwardRows.saveSubaward;
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
                "BBBBBBBBBBB2", "ACME FEDERAL LLC", "VA", "950.00", "2025-06-03");
        saveSubaward(jdbc, "S1", AWARDS.get(0), "E2QCEKQXLN48", "DVORAK, LLC", "1000.00", "2026-08-01");
        saveSubaward(jdbc, "S2", AWARDS.get(0), "E2QCEKQXLN48", "DVORAK, LLC", "500.00", "2026-08-02");
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
        json.extractingPath("$.results[0].subaward_count").isEqualTo(2);
        json.extractingPath("$.results[0].category.code").isEqualTo("OTHER");
        json.extractingPath("$.results[0].category.label").isEqualTo("Other");
        json.extractingPath("$.results[0].category.source").isEqualTo("baseline");
    }

    @Test
    void servesTheTaxonomyInDisplayOrder() {
        var result = mvc.get().uri("/api/v1/categories").exchange();

        assertThat(result).hasStatusOk().headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=300, public");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$[*].code").asArray().hasSize(13).startsWith("IT_SOFTWARE").endsWith("UNCLASSIFIABLE");
        json.extractingPath("$[2].label").isEqualTo("Cybersecurity");
        json.extractingPath("$[2].definition")
                .isEqualTo("Security operations, assessments, identity management, and information assurance");
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
        json.extractingPath("$.results[*].subaward_count").asArray().containsExactly(0, 2, 0);
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
    void countsEachFacetWithoutItsOwnSelectionButWithEveryOther() {
        var result = mvc.get().uri("/api/v1/awards/search?state=ID&fiscal_year=2026").exchange();

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(2);
        json.extractingPath("$.total_obligated").isEqualTo("4867000.00");
        // The state facet ignores state=ID, so Virginia still shows, but it applies fiscal_year=2026: 0 in VA.
        json.extractingPath("$.facets.state[*].value").asArray().containsExactly("ID");
        json.extractingPath("$.facets.state[0].count").isEqualTo(2);
        // The fiscal-year facet ignores fiscal_year=2026 but applies state=ID: both Idaho awards are FY2026.
        json.extractingPath("$.facets.fiscal_year[*].value").asArray().containsExactly("2026");
        json.extractingPath("$.facets.agency[0].value").isEqualTo("012");
        json.extractingPath("$.facets.agency[0].label").isEqualTo("Department of Agriculture");
        json.extractingPath("$.facets.agency[0].count").isEqualTo(2);
        json.extractingPath("$.facets.naics[0].label").isEqualTo("ALL OTHER TELECOMMUNICATIONS");
        // Every test award's PSC is F003, natural resources, which the baseline puts in OTHER.
        json.extractingPath("$.facets.category[*].value").asArray().containsExactly("OTHER");
        json.extractingPath("$.facets.category[0].count").isEqualTo(2);
        json.extractingPath("$.facets.category[0].label").isEqualTo("Other");
    }

    @Test
    void keepsOtherValuesOfASelectedFacetCountable() {
        var result = mvc.get().uri("/api/v1/awards/search?state=VA").exchange();

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(1);
        json.extractingPath("$.facets.state[*].value").asArray().containsExactly("ID", "VA");
        json.extractingPath("$.facets.state[*].count").asArray().containsExactly(2, 1);
        json.extractingPath("$.facets.fiscal_year[*].value").asArray().containsExactly("2025");
    }

    @Test
    void limitsTheSameSearchToOneRecipient() {
        var result = mvc.get().uri("/api/v1/recipients/{uei}/awards?state=ID", "mn5krx2w9r46").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(1);
        json.extractingPath("$.results[0].award_id").isEqualTo(AWARDS.get(1));
        // The recipient filter narrows facet counts too; Skyline's Idaho award isn't counted.
        json.extractingPath("$.facets.state[0].count").isEqualTo(1);
    }

    @Test
    void checksTheUeiAndTheParametersOfARecipientSearch() {
        var badUei = mvc.get().uri("/api/v1/recipients/{uei}/awards", "MN5KRX2W9").exchange();
        var badSize = mvc.get().uri("/api/v1/recipients/{uei}/awards?size=0", "MN5KRX2W9R46").exchange();

        assertThat(badUei).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badUei).bodyJson().extractingPath("$.type").isEqualTo("invalid-uei");
        assertThat(badSize).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badSize).bodyJson().extractingPath("$.type").isEqualTo("invalid-search-parameters");
    }

    @Test
    void suggestsRecipientsAsTheirNamesAreTyped() {
        var partial = mvc.get().uri("/api/v1/recipients/suggest?q=nomadic ll").exchange();
        var shared = mvc.get().uri("/api/v1/recipients/suggest?q=llc").exchange();

        assertThat(partial).hasStatusOk();
        assertThat(partial).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
        var json = assertThat(partial).bodyJson();
        json.extractingPath("$[*].uei").asArray().containsExactly("MN5KRX2W9R46");
        json.extractingPath("$[0].name").isEqualTo("NOMADIC LAND CAMPS, LLC");
        json.extractingPath("$[0].award_count").isEqualTo(1);
        assertThat(shared).bodyJson().extractingPath("$[*].name").asArray()
                .containsExactly("ACME FEDERAL LLC", "NOMADIC LAND CAMPS, LLC");
    }

    @Test
    void wantsAtLeastTwoCharactersToSuggestFrom() {
        var result = mvc.get().uri("/api/v1/recipients/suggest?q= n ").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("q");
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
