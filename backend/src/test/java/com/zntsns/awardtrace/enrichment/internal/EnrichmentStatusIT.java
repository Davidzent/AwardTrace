package com.zntsns.awardtrace.enrichment.internal;

import static com.zntsns.awardtrace.AwardRows.describe;
import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.ElasticsearchTestConfiguration;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Production runs the enricher in the API's process (doc 13), so the status reports on it. This context is the only
 * one with both roles, so its counters start at zero. It starts a real Tomcat, as production does: MockMvc's mock
 * servlet context accepts what Tomcat rejects, such as a relative path that climbs above its root.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"api", "enricher"})
@Import({TestcontainersConfiguration.class, ElasticsearchTestConfiguration.class, FakeClaudeConfiguration.class})
class EnrichmentStatusIT {

    private static final String SHARED = "5".repeat(64);
    private static final String FAILED = "f".repeat(64);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Enricher enricher;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MeterRegistry meters;

    @AfterEach
    void emptyTables() {
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void reportsTheBreakerCoverageCacheAndSpend() {
        List.of("CONT_AWD_FIRST", "CONT_AWD_SECOND", "CONT_AWD_RETRY", "CONT_AWD_UNDESCRIBED")
                .forEach(award -> saveAward(jdbc, award, 1, "1000.00", false));
        saveAward(jdbc, "CONT_AWD_DELETED", 2, "0.00", true);
        describe(jdbc, SHARED, "CONT_AWD_FIRST", "CONT_AWD_SECOND", "CONT_AWD_DELETED");
        describe(jdbc, FAILED, "CONT_AWD_RETRY");
        jdbc.sql("""
                INSERT INTO classification (description_hash, category, reason_code, model, prompt_version)
                VALUES (:hash, 'UNCLASSIFIABLE', 'FAILED', 'claude-haiku-4-5', 'v1')
                """).param("hash", FAILED).update();
        // One miss, which goes to the model, then one hit.
        enricher.enrich(List.of("CONT_AWD_FIRST", "CONT_AWD_SECOND"));
        enricher.enrich(List.of("CONT_AWD_FIRST"));
        // The request cost $0.0011; the backfill's spend has its own cap, so it isn't counted.
        jdbc.sql("UPDATE enrichment_spend SET usd = usd + 0.14 WHERE path = 'live'").update();
        jdbc.sql("""
                INSERT INTO enrichment_spend (day, path, requests, input_tokens, output_tokens, cache_write_tokens,
                                              cache_read_tokens, usd)
                VALUES (:today, 'backfill', 1, 0, 0, 0, 0, 5.00)
                """).param("today", LocalDate.now(ZoneOffset.UTC)).update();

        var json = assertThat(mvc.get().uri("/api/v1/status").exchange()).bodyJson();

        json.extractingPath("$.enrichment.enabled").isEqualTo(true);
        json.extractingPath("$.enrichment.breaker_state").isEqualTo("CLOSED");
        // Two of the three live, described awards; the failed one waits for the backfill.
        json.extractingPath("$.enrichment.coverage_pct").isEqualTo(66.7);
        json.extractingPath("$.enrichment.cache_hit_rate_pct").isEqualTo(50.0);
        json.extractingPath("$.enrichment.spend_today_usd").isEqualTo("0.14");
        json.extractingPath("$.enrichment.daily_cap_usd").isEqualTo("1.00");
        json.extractingPath("$.pipeline.lag.enricher").isEqualTo(0);
        assertThat(meters.get("awardtrace.enricher.calls").tag("result", "ok").counter().count()).isEqualTo(1);
        assertThat(meters.get("awardtrace.enricher.spend.usd").tag("period", "today").gauge().value())
                .isEqualTo(0.1411);
    }
}
