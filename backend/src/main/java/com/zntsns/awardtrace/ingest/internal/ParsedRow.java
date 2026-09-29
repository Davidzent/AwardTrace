package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.ingest.SubawardReported;

/** The outcome of parsing one data row. {@code rowNumber} counts data rows from 1, excluding the header. */
sealed interface ParsedRow {

    long rowNumber();

    record Ingested(long rowNumber, ContractTransactionIngested event) implements ParsedRow {
    }

    record Deleted(long rowNumber, ContractTransactionDeleted event) implements ParsedRow {
    }

    record Reported(long rowNumber, SubawardReported event) implements ParsedRow {
    }

    /** Out of scope for AwardTrace, not an error. */
    record Skipped(long rowNumber, SkipReason reason) implements ParsedRow {
    }

    /** Unreadable: a missing key, or a value that isn't a valid number or date. */
    record Rejected(long rowNumber, String reason) implements ParsedRow {
    }

    enum SkipReason {
        IDV,
        BEFORE_SCOPE
    }
}
