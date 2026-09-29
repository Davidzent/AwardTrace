package com.zntsns.awardtrace.search.internal;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static com.zntsns.awardtrace.AwardRows.saveSearchableAward;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecipientControllerIT {

    private static final String UEI = "MN5KRX2W9R46";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    /**
     * Three live awards across two agencies, two NAICS codes, and two fiscal years; a deleted one that must not
     * count; another recipient's award; and a recipient whose only award is deleted.
     */
    @BeforeAll
    void saveAwards() {
        AwardRows.emptyTables(jdbc);
        saveSearchableAward(jdbc, "CONT_AWD_CAMPS", "12024B26M0522", "Nomadic land camps", UEI,
                "NOMADIC LAND CAMPS, LLC", "ID", "55000.00", "2026-08-24");
        saveSearchableAward(jdbc, "CONT_AWD_SHOWERS", "12024B25M0100", "Mobile shower units", UEI,
                "NOMADIC LAND CAMPS, LLC", "ID", "950.00", "2025-06-03");
        saveSearchableAward(jdbc, "CONT_AWD_NETWORK", "70FA2026C0001", "Network design", UEI,
                "NOMADIC LAND CAMPS, LLC", "ID", "100000.00", "2026-08-01");
        jdbc.sql("INSERT INTO agency (code, level, name) VALUES ('070', 'toptier', 'Department of Homeland Security')")
                .update();
        jdbc.sql("""
                UPDATE award SET awarding_toptier_code = '070', awarding_subtier_code = NULL, naics_code = '541512',
                                 naics_description = 'COMPUTER SYSTEMS DESIGN SERVICES'
                WHERE award_id = 'CONT_AWD_NETWORK'
                """).update();
        saveAward(jdbc, "CONT_AWD_WITHDRAWN", 2, "999.00", true);
        jdbc.sql("""
                UPDATE recipient SET parent_uei = 'PPPPPPPPPPP1', parent_name = 'NOMADIC HOLDINGS INC', city = 'BOISE',
                                     state_code = 'ID', country_code = 'USA'
                WHERE uei = :uei
                """).param("uei", UEI).update();
        saveSearchableAward(jdbc, "CONT_AWD_SOFTWARE", "12318726F0042", "Cloud software licenses", "BBBBBBBBBBB2",
                "ACME FEDERAL LLC", "VA", "7000.00", "2026-01-15");
        saveSearchableAward(jdbc, "CONT_AWD_GONE", "12024B26M0999", "Withdrawn order", "DDDDDDDDDDD4",
                "GONE LLC", "ID", "10.00", "2026-02-01");
        jdbc.sql("UPDATE award SET deleted_at = now() WHERE award_id = 'CONT_AWD_GONE'").update();
    }

    @AfterAll
    void emptyTables() {
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void rollsUpTheRecipientsLiveAwards() {
        var result = mvc.get().uri("/api/v1/recipients/{uei}", UEI).exchange();

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=300, public");
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.name").isEqualTo("NOMADIC LAND CAMPS, LLC");
        json.extractingPath("$.parent.uei").isEqualTo("PPPPPPPPPPP1");
        json.extractingPath("$.location.city").isEqualTo("BOISE");
        json.extractingPath("$.totals.award_count").isEqualTo(3);
        json.extractingPath("$.totals.total_obligated").isEqualTo("155950.00");
        json.extractingPath("$.top_agencies[*].code").asArray().containsExactly("070", "012");
        json.extractingPath("$.top_agencies[1].name").isEqualTo("Department of Agriculture");
        json.extractingPath("$.top_agencies[1].award_count").isEqualTo(2);
        json.extractingPath("$.top_agencies[1].total_obligated").isEqualTo("55950.00");
        json.extractingPath("$.top_naics[*].code").asArray().containsExactly("541512", "517810");
        json.extractingPath("$.top_naics[0].name").isEqualTo("COMPUTER SYSTEMS DESIGN SERVICES");
        json.extractingPath("$.awards_by_fiscal_year[*].fiscal_year").asArray().containsExactly(2025, 2026);
        json.extractingPath("$.awards_by_fiscal_year[*].total_obligated").asArray()
                .containsExactly("950.00", "155000.00");
        json.extractingPath("$.first_action_date").isEqualTo("2025-06-03");
        json.extractingPath("$.last_action_date").isEqualTo("2026-08-24");
    }

    @Test
    void findsARecipientByALowerCaseUei() {
        var result = mvc.get().uri("/api/v1/recipients/{uei}", "bbbbbbbbbbb2").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.totals.total_obligated").isEqualTo("7000.00");
        assertThat(result).bodyJson().extractingPath("$.parent").isNull();
    }

    @Test
    void treatsARecipientWithoutLiveAwardsAsMissing() {
        for (String uei : new String[] {"DDDDDDDDDDD4", "ZZZZZZZZZZZ9"}) {
            var result = mvc.get().uri("/api/v1/recipients/{uei}", uei).exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("recipient-not-found");
        }
    }

    @Test
    void refusesAMalformedUei() {
        var result = mvc.get().uri("/api/v1/recipients/{uei}", "MN5KRX2W9").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("invalid-uei");
    }
}
