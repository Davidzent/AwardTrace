package com.zntsns.awardtrace.enrichment.internal;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/** The hand-labeled gold set, {@code eval/gold.csv} (doc 09). */
final class GoldSet {

    /** @param pscCode the award's product or service code, which the baseline categorizes; null when it has none */
    record Row(String descriptionHash, String description, String pscCode, String label) {
    }

    private static final CsvMapper CSV = new CsvMapper();

    private GoldSet() {
    }

    /**
     * Every row, once each has a label that is one of the 14 codes. An unlabeled or mislabeled row fails the whole
     * set, before any request is sent: a partly labeled set would cost money and score nothing.
     */
    static List<Row> read(Path file) throws IOException {
        var rows = new ArrayList<Row>();
        try (Reader text = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                MappingIterator<Map<String, String>> lines = CSV.readerForMapOf(String.class)
                        .with(CsvSchema.emptySchema().withHeader())
                        .readValues(text)) {
            while (lines.hasNext()) {
                Map<String, String> line = lines.next();
                String label = line.getOrDefault("label", "").strip();
                if (!ClassificationValidation.CATEGORIES.contains(label)) {
                    throw new IllegalArgumentException("Row %d of %s has %s, not one of the 14 category codes"
                            .formatted(rows.size() + 1, file, label.isEmpty() ? "no label" : "label " + label));
                }
                String psc = line.getOrDefault("psc_code", "").strip();
                rows.add(new Row(line.get("description_hash"), line.get("description"), psc.isEmpty() ? null : psc,
                        label));
            }
        }
        return rows;
    }
}
