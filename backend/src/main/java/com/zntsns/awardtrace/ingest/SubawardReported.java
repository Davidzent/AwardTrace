package com.zntsns.awardtrace.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One subaward as its prime recipient reported it (ADR 0014), published to {@code subawards.v1} keyed by the prime
 * award ID. A report can be revised; {@code sourceModifiedAt} versions it, and the latest revision wins.
 *
 * @param subawardKey the report's ID in SAM.gov, unique per subaward
 * @param primeAwardId the prime award, which may be outside AwardTrace's scope, such as an IDV or an older contract
 * @param amount negative for a correction that takes money back
 */
public record SubawardReported(
        String subawardKey,
        Instant sourceModifiedAt,
        String primeAwardId,
        String primeRecipientUei,
        String primeRecipientName,
        String subRecipientUei,
        String subRecipientName,
        String subawardNumber,
        BigDecimal amount,
        LocalDate actionDate,
        String description) {
}
