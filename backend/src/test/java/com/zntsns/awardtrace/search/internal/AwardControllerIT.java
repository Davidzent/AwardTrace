package com.zntsns.awardtrace.search.internal;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static com.zntsns.awardtrace.AwardRows.saveTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class AwardControllerIT {

    private static final String AWARD_ID = "CONT_AWD_12024B26M0522_12C2_12024B24T7051_12C2";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void returnsTheAwardWithItsModificationsNewestFirst() {
        saveAward(jdbc, AWARD_ID, 3, "55000.00", false);
        saveTransaction(jdbc, AWARD_ID, "0", "2026-07-16", "27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00002", "2026-07-16", "-27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00001", "2026-08-24", "27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00003", "2026-09-01", "100.00", true);

        var result = mvc.get().uri("/api/v1/awards/{awardId}", AWARD_ID).exchange();

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).headers()
                .hasValue(HttpHeaders.ETAG, "\"3\"")
                .hasValue(HttpHeaders.CACHE_CONTROL, "max-age=300, public");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.award_id").isEqualTo(AWARD_ID);
        json.extractingPath("$.total_obligated").isEqualTo("55000.00");
        json.extractingPath("$.fiscal_year").isEqualTo(2026);
        json.extractingPath("$.last_action_date").isEqualTo("2026-08-24");
        json.extractingPath("$.recipient.name").isEqualTo("NOMADIC LAND CAMPS, LLC");
        json.extractingPath("$.agency.name").isEqualTo("Department of Agriculture");
        json.extractingPath("$.agency.subtier_name").isEqualTo("Forest Service");
        json.extractingPath("$.funding_agency").isNull();
        json.extractingPath("$.period_of_performance.end").isNull();
        json.extractingPath("$.transactions[*].modification_number").asArray()
                .containsExactly("P00001", "P00002", "0");
        json.extractingPath("$.transactions[1].federal_action_obligation").isEqualTo("-27500.00");
        json.extractingPath("$.transactions_truncated").isEqualTo(false);
        json.extractingPath("$.usaspending_url").isEqualTo("https://www.usaspending.gov/award/" + AWARD_ID + "/");
    }

    @Test
    void answersNotModifiedWhenTheClientHoldsTheCurrentVersion() {
        saveAward(jdbc, AWARD_ID, 3, "55000.00", false);

        var result = mvc.get().uri("/api/v1/awards/{awardId}", AWARD_ID).header(HttpHeaders.IF_NONE_MATCH, "\"3\"")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_MODIFIED);
        assertThat(result).body().isEmpty();
    }

    @Test
    void describesAMissingAwardAsProblemDetails() {
        var result = mvc.get().uri("/api/v1/awards/{awardId}", "CONT_AWD_NONE").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("award-not-found");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("No award with ID CONT_AWD_NONE");
    }

    @Test
    void treatsADeletedAwardAsMissing() {
        saveAward(jdbc, AWARD_ID, 2, "0.00", true);

        assertThat(mvc.get().uri("/api/v1/awards/{awardId}", AWARD_ID).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }
}
