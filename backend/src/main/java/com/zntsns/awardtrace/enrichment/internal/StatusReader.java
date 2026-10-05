package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.EnrichmentStatus;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the enricher's state for the status page. Money leaves the API with two decimal places (doc 07). */
@Component
@Profile("enricher")
class StatusReader implements EnrichmentStatus.Reader {

    /** A FAILED classification waits for the next backfill, so it doesn't count as covered. */
    private static final String COVERAGE = """
            SELECT round(100.0 * count(c.description_hash) / nullif(count(*), 0), 1)
            FROM award a
            LEFT JOIN classification c
                   ON c.description_hash = a.description_hash AND c.reason_code IS DISTINCT FROM 'FAILED'
            WHERE a.deleted_at IS NULL AND a.description_hash IS NOT NULL
            """;

    private final JdbcClient jdbc;
    private final CircuitBreaker breaker;
    private final Enricher enricher;
    private final SpendLedger ledger;
    private final EnrichmentProperties properties;
    private final Clock clock;

    StatusReader(JdbcClient jdbc, CircuitBreaker breaker, Enricher enricher, SpendLedger ledger,
            EnrichmentProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.breaker = breaker;
        this.enricher = enricher;
        this.ledger = ledger;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public EnrichmentStatus read() {
        // With no described award, nullif() makes the result null, which single() rejects.
        Double coverage = jdbc.sql(COVERAGE).query(Double.class).optional().orElse(null);
        return new EnrichmentStatus(true, breaker.state().name(), coverage, enricher.cacheHitRatePct(),
                ledger.spent(LocalDate.now(clock), SpendGuard.LIVE).setScale(2, RoundingMode.HALF_UP),
                properties.dailyCapUsd().setScale(2, RoundingMode.HALF_UP));
    }
}
