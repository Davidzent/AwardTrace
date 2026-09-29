package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Deleted;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Ingested;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Rejected;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.SkipReason;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Skipped;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/**
 * Reads a USAspending contract CSV, full or delta, into one {@link ParsedRow} per data row. Maps source column
 * names to event fields, so nothing downstream sees a source column name.
 */
class ContractFileParser {

    /** FY2025 starts on 2024-10-01. Earlier actions are out of scope (ADR 0012). */
    static final LocalDate SCOPE_START = LocalDate.of(2024, 10, 1);

    private static final Pattern FILE_DATE = Pattern.compile("_(\\d{8})_\\d+\\.csv$");

    // Live rows use "2026-08-17 23:02:10+00".
    private static final DateTimeFormatter MODIFIED_AT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .appendOffset("+HH", "+00")
            .toFormatter();

    private final CsvMapper mapper = new CsvMapper();

    /** The stream reads lazily from {@code csv}; close it to release the reader. */
    Stream<ParsedRow> parse(Reader csv, String fileName) {
        LocalDate fileDate = fileDateOf(fileName);
        MappingIterator<Map<String, String>> rows = mapper.readerForMapOf(String.class)
                .with(CsvSchema.emptySchema().withHeader())
                .readValues(csv);
        AtomicLong rowNumber = new AtomicLong();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false)
                .map(row -> parseRow(new SourceRow(row), rowNumber.incrementAndGet(), fileName, fileDate))
                .onClose(rows::close);
    }

    private static ParsedRow parseRow(SourceRow row, long rowNumber, String fileName, LocalDate fileDate) {
        String transactionId = row.text("contract_transaction_unique_key");
        if (transactionId == null) {
            return new Rejected(rowNumber, "missing contract_transaction_unique_key");
        }
        if ("D".equals(row.optionalText("correction_delete_ind"))) {
            String awardId = awardIdOf(transactionId);
            return awardId == null
                    ? new Rejected(rowNumber, "can't derive an award key from " + transactionId)
                    : new Deleted(rowNumber, new ContractTransactionDeleted(transactionId, awardId, fileName, fileDate));
        }
        if ("IDV".equals(row.text("award_or_idv_flag"))) {
            return new Skipped(rowNumber, SkipReason.IDV);
        }
        String awardId = row.text("contract_award_unique_key");
        if (awardId == null) {
            return new Rejected(rowNumber, "missing contract_award_unique_key");
        }
        try {
            LocalDate actionDate = row.date("action_date");
            if (actionDate != null && actionDate.isBefore(SCOPE_START)) {
                return new Skipped(rowNumber, SkipReason.BEFORE_SCOPE);
            }
            return new Ingested(rowNumber, new ContractTransactionIngested(
                    transactionId,
                    awardId,
                    fileName,
                    fileDate,
                    row.instant("last_modified_date"),
                    row.text("award_id_piid"),
                    row.text("parent_award_id_piid"),
                    row.text("modification_number"),
                    row.text("award_type_code"),
                    row.text("action_type_code"),
                    actionDate,
                    row.money("federal_action_obligation"),
                    row.money("total_dollars_obligated"),
                    row.money("potential_total_value_of_award"),
                    row.date("period_of_performance_start_date"),
                    row.date("period_of_performance_current_end_date"),
                    row.text("awarding_agency_code"),
                    row.text("awarding_agency_name"),
                    row.text("awarding_sub_agency_code"),
                    row.text("awarding_sub_agency_name"),
                    row.text("funding_agency_code"),
                    row.text("funding_agency_name"),
                    row.text("recipient_uei"),
                    row.text("recipient_name"),
                    row.text("recipient_parent_uei"),
                    row.text("recipient_parent_name"),
                    row.text("recipient_city_name"),
                    row.text("recipient_state_code"),
                    row.text("recipient_country_code"),
                    row.text("primary_place_of_performance_state_code"),
                    row.text("primary_place_of_performance_country_code"),
                    row.text("naics_code"),
                    row.text("naics_description"),
                    row.text("product_or_service_code"),
                    row.text("product_or_service_code_description"),
                    row.text("transaction_description"),
                    row.text("prime_award_base_transaction_description")));
        } catch (DateTimeException | NumberFormatException e) {
            return new Rejected(rowNumber, e.getMessage());
        }
    }

    /**
     * A transaction key is {@code agency_parentAgency_piid_modification_parentPiid_number}, and its award key is
     * {@code CONT_AWD_piid_agency_parentPiid_parentAgency}.
     */
    static String awardIdOf(String transactionId) {
        String[] parts = transactionId.split("_", -1);
        if (parts.length != 6) {
            return null;
        }
        return "CONT_AWD_" + parts[2] + "_" + parts[0] + "_" + parts[4] + "_" + parts[1];
    }

    private static LocalDate fileDateOf(String fileName) {
        var matcher = FILE_DATE.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No generation date in source file name " + fileName);
        }
        return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private record SourceRow(Map<String, String> values) {

        /** Fails the whole file, not the row: a missing column means the source format changed. */
        String text(String column) {
            if (!values.containsKey(column)) {
                throw new IllegalArgumentException("Source file has no column " + column);
            }
            return optionalText(column);
        }

        String optionalText(String column) {
            String value = values.get(column);
            return value == null || value.isBlank() ? null : value.strip();
        }

        LocalDate date(String column) {
            String value = text(column);
            return value == null ? null : LocalDate.parse(value);
        }

        Instant instant(String column) {
            String value = text(column);
            return value == null ? null : OffsetDateTime.parse(value, MODIFIED_AT).toInstant();
        }

        BigDecimal money(String column) {
            String value = text(column);
            return value == null ? null : new BigDecimal(value);
        }
    }
}
