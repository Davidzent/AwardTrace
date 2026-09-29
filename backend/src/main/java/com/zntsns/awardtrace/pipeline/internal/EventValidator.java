package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Checks each event against every rule the database enforces, so a write never fails on bad data. A problem is a
 * stable reason code, which the dead-letter topic carries in its {@code awardtrace-reason} header.
 */
final class EventValidator {

    private static final Set<String> AWARD_TYPES = Set.of("A", "B", "C", "D");

    private record Field(String name, Object value) {
    }

    private EventValidator() {
    }

    static Optional<String> problem(ContractTransactionIngested event) {
        if (event.recipientUei() == null) {
            return Optional.of("MISSING_UEI");
        }
        if (event.recipientUei().length() != 12) {
            return Optional.of("INVALID_UEI");
        }
        if (event.recipientParentUei() != null && event.recipientParentUei().length() != 12) {
            return Optional.of("INVALID_PARENT_UEI");
        }
        if (!AWARD_TYPES.contains(event.awardType())) {
            return Optional.of("UNKNOWN_AWARD_TYPE");
        }
        return missing(
                new Field("TRANSACTION_ID", event.transactionId()),
                new Field("AWARD_ID", event.awardId()),
                new Field("SOURCE_FILE", event.sourceFile()),
                new Field("SOURCE_FILE_DATE", event.sourceFileDate()),
                new Field("SOURCE_MODIFIED_AT", event.sourceModifiedAt()),
                new Field("PIID", event.piid()),
                new Field("MODIFICATION_NUMBER", event.modificationNumber()),
                new Field("ACTION_DATE", event.actionDate()),
                new Field("FEDERAL_ACTION_OBLIGATION", event.federalActionObligation()),
                new Field("TOTAL_OBLIGATED", event.totalObligated()),
                new Field("AWARDING_TOPTIER_CODE", event.awardingToptierCode()),
                new Field("RECIPIENT_NAME", event.recipientName()));
    }

    static Optional<String> problem(ContractTransactionDeleted event) {
        return missing(
                new Field("TRANSACTION_ID", event.transactionId()),
                new Field("AWARD_ID", event.awardId()),
                new Field("SOURCE_FILE", event.sourceFile()),
                new Field("SOURCE_FILE_DATE", event.sourceFileDate()));
    }

    private static Optional<String> missing(Field... fields) {
        return Stream.of(fields).filter(field -> field.value() == null).findFirst()
                .map(field -> "MISSING_" + field.name());
    }
}
