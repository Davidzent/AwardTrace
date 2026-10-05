package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One run's gold-set scores, beside the PSC baseline's (doc 09; {@code eval/README.md} lists the measures). The
 * baseline has no answer for a row without a PSC and never answers {@code UNCLASSIFIABLE}, so accuracy over the rows
 * not labeled {@code UNCLASSIFIABLE} is the fair comparison on clear descriptions.
 */
record EvalReport(String model, String promptVersion, Instant ranAt, List<Scored> rows, int requests,
        int refusedRequests, Usage usage, BigDecimal cost) {

    /**
     * @param answer the model's category
     * @param reason why the answer is {@code UNCLASSIFIABLE}: {@code VAGUE}, {@code REFUSAL}, or {@code FAILED}
     * @param baseline the PSC baseline's category; null when the row has no PSC or the map doesn't cover it
     */
    record Scored(GoldSet.Row gold, String answer, String reason, String baseline) {
    }

    private static final String UNCLASSIFIABLE = "UNCLASSIFIABLE";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd 'at' HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    /** Column heads for the confusion matrix, which is too wide with whole codes. */
    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("IT_SOFTWARE", "ITS"), Map.entry("IT_INFRASTRUCTURE", "ITI"), Map.entry("CYBERSECURITY", "CYB"),
            Map.entry("PROFESSIONAL_SERVICES", "PRO"), Map.entry("ENGINEERING_RESEARCH", "ENG"),
            Map.entry("CONSTRUCTION_FACILITIES", "CON"), Map.entry("HEALTH_MEDICAL", "HEA"),
            Map.entry("DEFENSE_SYSTEMS", "DEF"), Map.entry("LOGISTICS_TRANSPORT", "LOG"),
            Map.entry("SUPPLIES_EQUIPMENT", "SUP"), Map.entry("TRAINING_EDUCATION", "TRA"),
            Map.entry("NATURAL_RESOURCES", "NAT"), Map.entry("OTHER", "OTH"), Map.entry(UNCLASSIFIABLE, "UNC"));

    long modelCorrect(boolean clearOnly) {
        return count(row -> row.answer().equals(row.gold().label()), clearOnly);
    }

    long baselineCorrect(boolean clearOnly) {
        return count(row -> row.gold().label().equals(row.baseline()), clearOnly);
    }

    /** Rows not labeled {@code UNCLASSIFIABLE}, the descriptions the labeler could classify. */
    long clearRows() {
        return count(row -> true, true);
    }

    long agreement() {
        return count(row -> row.answer().equals(row.baseline()), false);
    }

    /** The share of the model's answers in a category that the labels agree with; null when it never answered it. */
    Double modelPrecision(String category) {
        return share(count(row -> row.answer().equals(category) && row.gold().label().equals(category), false),
                count(row -> row.answer().equals(category), false));
    }

    /** The share of rows labeled with a category that the model also gave it; null when no row has the label. */
    Double modelRecall(String category) {
        return share(count(row -> row.answer().equals(category) && row.gold().label().equals(category), false),
                labeled(category));
    }

    Double baselinePrecision(String category) {
        return share(count(row -> category.equals(row.baseline()) && row.gold().label().equals(category), false),
                count(row -> category.equals(row.baseline()), false));
    }

    Double baselineRecall(String category) {
        return share(count(row -> category.equals(row.baseline()) && row.gold().label().equals(category), false),
                labeled(category));
    }

    /** How many rows have this label and this answer from the model. */
    long confused(String label, String answer) {
        return count(row -> row.gold().label().equals(label) && row.answer().equals(answer), false);
    }

    String markdown() {
        int n = rows.size();
        long unclassifiable = count(row -> row.answer().equals(UNCLASSIFIABLE), false);
        var out = new StringBuilder();
        out.append("# ").append(model).append(", prompt ").append(promptVersion).append("\n\n");
        out.append("The gold set's ").append(n).append(" descriptions, classified on ").append(MINUTE.format(ranAt))
                .append(" and scored against their hand labels and the PSC baseline. `eval/README.md` describes how ")
                .append("the set was drawn and labeled.\n\n");

        out.append("## Scores\n\n");
        out.append("| Measure | ").append(model).append(" | PSC baseline |\n|---|---|---|\n");
        out.append("| Accuracy | ").append(ratio(modelCorrect(false), n)).append(" | ")
                .append(ratio(baselineCorrect(false), n)).append(" |\n");
        out.append("| Accuracy on rows not labeled UNCLASSIFIABLE | ").append(ratio(modelCorrect(true), clearRows()))
                .append(" | ").append(ratio(baselineCorrect(true), clearRows())).append(" |\n");
        out.append("| Answered UNCLASSIFIABLE | ").append(ratio(unclassifiable, n)).append("; the label agreed for ")
                .append(confused(UNCLASSIFIABLE, UNCLASSIFIABLE)).append(" | Never |\n");
        out.append("| Refused | ").append(counted(refusedRequests, "request")).append(", ")
                .append(counted(count(row -> "REFUSAL".equals(row.reason()), false), "description")).append(" | - |\n");
        out.append("| Failed validation twice | ")
                .append(counted(count(row -> "FAILED".equals(row.reason()), false), "description")).append(" | - |\n");
        out.append("| Agreement with the baseline | ").append(ratio(agreement(), n)).append(" | - |\n\n");

        out.append("## Usage\n\n| Measure | Value |\n|---|---|\n");
        out.append("| Requests | ").append(requests).append(" |\n");
        out.append("| Input tokens | ").append(thousands(usage.inputTokens())).append(" |\n");
        out.append("| Output tokens, thinking included | ").append(thousands(usage.outputTokens())).append(" |\n");
        out.append("| Cache writes and reads | ").append(thousands(usage.cacheWriteTokens())).append(" and ")
                .append(thousands(usage.cacheReadTokens())).append(" |\n");
        out.append("| Cost | ").append(dollars(cost)).append(" |\n");
        out.append("| Cost per 100 descriptions | ").append(dollars(costPer100())).append(" |\n\n");

        out.append("## Precision and recall\n\n");
        out.append("A dash means no answer or no label in that category.\n\n");
        out.append("| Category | Labeled | Model precision | Model recall | Baseline precision | Baseline recall |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (String category : ClassificationValidation.CATEGORIES) {
            out.append("| ").append(category).append(" | ").append(labeled(category)).append(" | ")
                    .append(percent(modelPrecision(category))).append(" | ").append(percent(modelRecall(category)))
                    .append(" | ").append(percent(baselinePrecision(category))).append(" | ")
                    .append(percent(baselineRecall(category))).append(" |\n");
        }

        out.append("\n## Confusion matrix\n\n");
        out.append("Rows are the labels; columns are the model's answers, under the first letters of their codes.\n\n");
        out.append("| Label |");
        for (String category : ClassificationValidation.CATEGORIES) {
            out.append(' ').append(SHORT.get(category)).append(" |");
        }
        out.append("\n|---|").append("---|".repeat(ClassificationValidation.CATEGORIES.size())).append('\n');
        for (String label : ClassificationValidation.CATEGORIES) {
            out.append("| ").append(label).append(" |");
            for (String answer : ClassificationValidation.CATEGORIES) {
                long cell = confused(label, answer);
                out.append(' ').append(cell == 0 ? "" : Long.toString(cell)).append(" |");
            }
            out.append('\n');
        }

        out.append("\n## Misses\n\nEvery row where the model's answer differs from the label.\n\n");
        out.append("| Description | Label | Model | Baseline |\n|---|---|---|---|\n");
        for (Scored row : rows) {
            if (!row.answer().equals(row.gold().label())) {
                out.append("| ").append(cell(row.gold().description())).append(" | ").append(row.gold().label())
                        .append(" | ").append(row.answer())
                        .append(row.reason() == null ? "" : " (" + row.reason() + ")").append(" | ")
                        .append(Objects.requireNonNullElse(row.baseline(), "-")).append(" |\n");
            }
        }
        return out.toString();
    }

    /** This run's row in {@code docs/results.md}, linking to its report. */
    String summaryRow(String reportLink) {
        return "| [%s](%s) | %s | %s | %s | %s | %s | %s | %s |".formatted(model, reportLink, promptVersion,
                percent(share(modelCorrect(false), rows.size())), percent(share(modelCorrect(true), clearRows())),
                percent(share(count(row -> row.answer().equals(UNCLASSIFIABLE), false), rows.size())),
                percent(share(agreement(), rows.size())), dollars(costPer100()), DAY.format(ranAt));
    }

    /** The baseline's row in {@code docs/results.md}. The labels never change, so neither does it. */
    String baselineSummaryRow() {
        return "| PSC baseline | - | %s | %s | 0.0%% | - | Free | %s |".formatted(
                percent(share(baselineCorrect(false), rows.size())), percent(share(baselineCorrect(true), clearRows())),
                DAY.format(ranAt));
    }

    private BigDecimal costPer100() {
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return cost.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(rows.size()), 6, RoundingMode.HALF_UP);
    }

    private long count(Predicate<Scored> test, boolean clearOnly) {
        return rows.stream()
                .filter(row -> !clearOnly || !row.gold().label().equals(UNCLASSIFIABLE))
                .filter(test)
                .count();
    }

    private long labeled(String category) {
        return count(row -> row.gold().label().equals(category), false);
    }

    private static Double share(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    private static String percent(Double share) {
        return share == null ? "-" : String.format(Locale.ROOT, "%.1f%%", 100 * share);
    }

    private static String ratio(long part, long whole) {
        return percent(share(part, whole)) + " (" + part + " of " + whole + ")";
    }

    private static String counted(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static String thousands(long count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    private static String dollars(BigDecimal amount) {
        return "$" + amount.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    /** A table cell: one line, with its pipes escaped. */
    private static String cell(String text) {
        return text.replaceAll("\\s+", " ").strip().replace("|", "\\|");
    }
}
