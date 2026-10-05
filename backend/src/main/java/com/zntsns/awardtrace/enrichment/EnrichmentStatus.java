package com.zntsns.awardtrace.enrichment;

import java.math.BigDecimal;

/**
 * The enricher's state, for the status page (doc 07).
 *
 * @param enabled whether the enricher runs in this process; the other fields are null when it doesn't
 * @param breakerState {@code CLOSED}, {@code OPEN}, or {@code HALF_OPEN}
 * @param coveragePct the percentage of live awards with a description whose description has a classification, not
 *     counting one that failed and waits for the next backfill; null without any such award
 * @param cacheHitRatePct the percentage of descriptions looked up since the process started that were already
 *     classified; null before the first lookup
 * @param spendTodayUsd what the live path has spent on the Claude API so far in the UTC day
 * @param dailyCapUsd the most the live path may spend in a UTC day
 */
public record EnrichmentStatus(boolean enabled, String breakerState, Double coveragePct, Double cacheHitRatePct,
        BigDecimal spendTodayUsd, BigDecimal dailyCapUsd) {

    public static final EnrichmentStatus DISABLED = new EnrichmentStatus(false, null, null, null, null, null);

    /** A bean only where the enricher runs. */
    public interface Reader {

        EnrichmentStatus read();
    }
}
