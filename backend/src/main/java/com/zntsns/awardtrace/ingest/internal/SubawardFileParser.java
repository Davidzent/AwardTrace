package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.SubawardReported;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Rejected;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Reported;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.SkipReason;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Skipped;
import java.io.Reader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Reads a generated USAspending subaward CSV (ADR 0014) into one {@link ParsedRow} per data row. Subawards under
 * IDVs and older contracts are kept: the prime award is out of scope, but the relationship between the two recipients
 * isn't.
 */
class SubawardFileParser {

    /** The stream reads lazily from {@code csv}; close it to release the reader. */
    Stream<ParsedRow> parse(Reader csv) {
        AtomicLong rowNumber = new AtomicLong();
        return SourceRow.read(csv).map(row -> parseRow(row, rowNumber.incrementAndGet()));
    }

    private static ParsedRow parseRow(SourceRow row, long rowNumber) {
        String subawardKey = row.text("subaward_sam_report_id");
        if (subawardKey == null) {
            return new Rejected(rowNumber, "missing subaward_sam_report_id");
        }
        String primeAwardId = row.text("prime_award_unique_key");
        if (primeAwardId == null) {
            return new Rejected(rowNumber, "missing prime_award_unique_key");
        }
        try {
            LocalDate actionDate = row.date("subaward_action_date");
            if (actionDate != null && actionDate.isBefore(ContractFileParser.SCOPE_START)) {
                return new Skipped(rowNumber, SkipReason.BEFORE_SCOPE);
            }
            return new Reported(rowNumber, new SubawardReported(
                    subawardKey,
                    row.instant("subaward_sam_report_last_modified_date"),
                    primeAwardId,
                    row.text("prime_awardee_uei"),
                    row.text("prime_awardee_name"),
                    row.text("subawardee_uei"),
                    row.text("subawardee_name"),
                    row.text("subaward_number"),
                    row.money("subaward_amount"),
                    actionDate,
                    row.text("subaward_description")));
        } catch (DateTimeException | NumberFormatException e) {
            return new Rejected(rowNumber, e.getMessage());
        }
    }
}
