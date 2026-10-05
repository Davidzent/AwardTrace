package com.zntsns.awardtrace.enrichment.internal;

import static com.zntsns.awardtrace.AwardRows.describe;
import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.errors.InternalServerException;
import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.enrichment.internal.CircuitBreaker.State;
import com.zntsns.awardtrace.enrichment.internal.Enricher.Result;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The live path's daily cap, $1.00 as configured, and its circuit breaker. A clock the tests move gives this class
 * its own context, so the breaker it opens never reaches the other enricher tests; each test ends with it closed.
 */
@SpringBootTest
@ActiveProfiles("enricher")
@Import({TestcontainersConfiguration.class, FakeClaudeConfiguration.class})
class EnricherBudgetIT {

    private static final List<String> AWARD = List.of("CONT_AWD_FIRST");

    @TestConfiguration
    static class MovingClock {

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock(Instant.parse("2026-10-05T12:00:00Z"));
        }
    }

    @Autowired
    Enricher enricher;

    @Autowired
    FakeClaude claude;

    @Autowired
    CircuitBreaker breaker;

    @Autowired
    MovableClock clock;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void saveADescribedAward() {
        saveAward(jdbc, "CONT_AWD_FIRST", 3, "1000.00", false);
        describe(jdbc, "5".repeat(64), "CONT_AWD_FIRST");
    }

    @AfterEach
    void reset() {
        AwardRows.emptyTables(jdbc);
        claude.reset();
    }

    @Test
    void sendsNothingWhenTheCapCannotCoverOneMoreRequestAndTriesAgainTheNextDay() {
        // $0.01 is left under the cap; a request may cost about $0.02.
        spendToday("0.99");

        assertThatThrownBy(() -> enricher.enrich(AWARD)).isInstanceOf(SpendGuard.Refused.class);

        assertThat(claude.requests()).isEmpty();
        assertThat(breaker.state()).isEqualTo(State.OPEN);
        // The blocked enricher wrote nothing, so it left the pipeline's awards and outbox as they were.
        assertThat(count("classification")).isZero();
        assertThat(count("outbox")).isZero();

        // Midnight UTC: a new day under the cap, and long past the breaker's 15 minutes.
        clock.advance(Duration.ofHours(12));

        assertThat(enricher.enrich(AWARD)).isEqualTo(new Result(0, 1, 1));
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void stopsCallingAfterFiveConsecutiveErrorsAndTriesAgainFifteenMinutesLater() {
        claude.fail(true);
        for (int i = 0; i < CircuitBreaker.CONSECUTIVE_ERRORS_TO_OPEN; i++) {
            assertThatThrownBy(() -> enricher.enrich(AWARD)).isInstanceOf(InternalServerException.class);
        }

        assertThat(breaker.state()).isEqualTo(State.OPEN);
        assertThatThrownBy(() -> enricher.enrich(AWARD)).isInstanceOf(SpendGuard.Refused.class);
        assertThat(claude.requests()).hasSize(5);

        claude.fail(false);
        clock.advance(CircuitBreaker.OPEN_FOR);

        assertThat(enricher.enrich(AWARD)).isEqualTo(new Result(0, 1, 1));
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
        assertThat(claude.requests()).hasSize(6);
    }

    private void spendToday(String usd) {
        jdbc.sql("""
                INSERT INTO enrichment_spend (day, path, requests, input_tokens, output_tokens, cache_write_tokens,
                                              cache_read_tokens, usd)
                VALUES (:day, 'live', 160, 0, 0, 0, 0, :usd)
                """)
                .param("day", LocalDate.now(clock))
                .param("usd", new BigDecimal(usd))
                .update();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
