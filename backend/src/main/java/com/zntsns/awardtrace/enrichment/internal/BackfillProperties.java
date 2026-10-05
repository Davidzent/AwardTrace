package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code backfill} task's settings (doc 09).
 *
 * @param capUsd the most one run may spend, given on each run: a batch whose worst case could take the run past it
 *     isn't submitted
 * @param requestsPerBatch how many groups of 25 one Message Batch carries. Each batch's worst case counts against the
 *     cap before it's submitted, so smaller batches let a cap go further
 * @param pollInterval how often to check whether a submitted batch has ended
 */
@ConfigurationProperties("awardtrace.enrichment.backfill")
record BackfillProperties(BigDecimal capUsd, int requestsPerBatch, Duration pollInterval) {
}
