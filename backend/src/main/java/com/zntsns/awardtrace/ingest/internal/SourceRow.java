package com.zntsns.awardtrace.ingest.internal;

import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/** One data row of a USAspending CSV, read by column name. A blank value reads as null. */
record SourceRow(Map<String, String> values) {

    private static final CsvMapper CSV = new CsvMapper();

    // USAspending writes timestamps such as "2026-08-17 23:02:10+00".
    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .appendOffset("+HH", "+00")
            .toFormatter();

    /** The rows of a CSV with a header line, read lazily; close the stream to release the reader. */
    static Stream<SourceRow> read(Reader csv) {
        MappingIterator<Map<String, String>> rows = CSV.readerForMapOf(String.class)
                .with(CsvSchema.emptySchema().withHeader())
                .readValues(csv);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(rows, Spliterator.ORDERED), false)
                .map(SourceRow::new)
                .onClose(rows::close);
    }

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
        return value == null ? null : OffsetDateTime.parse(value, TIMESTAMP).toInstant();
    }

    BigDecimal money(String column) {
        String value = text(column);
        return value == null ? null : new BigDecimal(value);
    }
}
