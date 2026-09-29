package com.zntsns.awardtrace.ingest;

import java.time.LocalDate;

/**
 * A delete row from a USAspending delta file. Delete rows carry no award key, so {@code awardId} is derived from
 * the transaction key, which encodes it.
 */
public record ContractTransactionDeleted(
        String transactionId,
        String awardId,
        String sourceFile,
        LocalDate sourceFileDate) {
}
