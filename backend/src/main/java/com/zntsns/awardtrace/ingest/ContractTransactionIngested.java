package com.zntsns.awardtrace.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One contract transaction as a USAspending source file published it. The award-level fields describe the award
 * as of this transaction. Blank source values are {@code null}; the pipeline decides which ones it requires.
 *
 * @param sourceFile the CSV file name, which with {@code sourceFileDate} versions the row (ADR 0012)
 */
public record ContractTransactionIngested(
        String transactionId,
        String awardId,
        String sourceFile,
        LocalDate sourceFileDate,
        Instant sourceModifiedAt,
        String piid,
        String parentPiid,
        String modificationNumber,
        String awardType,
        String actionTypeCode,
        LocalDate actionDate,
        BigDecimal federalActionObligation,
        BigDecimal totalObligated,
        BigDecimal potentialTotalValue,
        LocalDate popStartDate,
        LocalDate popEndDate,
        String awardingToptierCode,
        String awardingToptierName,
        String awardingSubtierCode,
        String awardingSubtierName,
        String fundingToptierCode,
        String fundingToptierName,
        String recipientUei,
        String recipientName,
        String recipientParentUei,
        String recipientParentName,
        String recipientCity,
        String recipientStateCode,
        String recipientCountryCode,
        String popStateCode,
        String popCountryCode,
        String naicsCode,
        String naicsDescription,
        String pscCode,
        String pscDescription,
        String transactionDescription,
        String awardDescription) {
}
