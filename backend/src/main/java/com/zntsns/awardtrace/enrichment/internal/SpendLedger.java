package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The enricher's Claude API spend per UTC day and path, in {@code enrichment_spend} (doc 09). */
@Component
@Profile("enricher")
class SpendLedger {

    private static final String ADD = """
            INSERT INTO enrichment_spend (day, path, requests, input_tokens, output_tokens, cache_write_tokens,
                                          cache_read_tokens, usd)
            VALUES (:day, :path, 1, :input, :output, :cacheWrite, :cacheRead, :usd)
            ON CONFLICT (day, path) DO UPDATE SET
                requests = enrichment_spend.requests + 1,
                input_tokens = enrichment_spend.input_tokens + EXCLUDED.input_tokens,
                output_tokens = enrichment_spend.output_tokens + EXCLUDED.output_tokens,
                cache_write_tokens = enrichment_spend.cache_write_tokens + EXCLUDED.cache_write_tokens,
                cache_read_tokens = enrichment_spend.cache_read_tokens + EXCLUDED.cache_read_tokens,
                usd = enrichment_spend.usd + EXCLUDED.usd
            """;

    private final JdbcClient jdbc;

    /** Gauges the live path's spend so far in the UTC day as {@code awardtrace.enricher.spend.usd}. */
    SpendLedger(JdbcClient jdbc, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        Gauge.builder("awardtrace.enricher.spend.usd", this,
                        ledger -> ledger.spent(LocalDate.now(clock), SpendGuard.LIVE).doubleValue())
                .tag("period", "today")
                .register(meters);
    }

    BigDecimal spent(LocalDate day, String path) {
        return jdbc.sql("SELECT usd FROM enrichment_spend WHERE day = :day AND path = :path")
                .param("day", day)
                .param("path", path)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    /** Adds one request's tokens and cost to the day's total. */
    void add(LocalDate day, String path, Usage usage, BigDecimal usd) {
        jdbc.sql(ADD)
                .param("day", day)
                .param("path", path)
                .param("input", usage.inputTokens())
                .param("output", usage.outputTokens())
                .param("cacheWrite", usage.cacheWriteTokens())
                .param("cacheRead", usage.cacheReadTokens())
                .param("usd", usd)
                .update();
    }
}
