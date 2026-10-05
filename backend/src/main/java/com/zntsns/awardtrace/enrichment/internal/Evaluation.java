package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.internal.EvalReport.Scored;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Classifies the gold set with the configured model and prompt version, and scores the answers and the PSC baseline
 * against the hand labels (doc 09). Requests go straight to the model, under the run's own cap rather than the live
 * path's: this writes the report files, and nothing to the database.
 */
@Component
@Profile("enricher")
class Evaluation {

    private static final String SUMMARY_HEAD = """
            # Classification results

            Each row scores one model and prompt version on the gold set in `eval/`, against the free PSC baseline \
            (ADR 0007). A model's categories become the site's default only if they beat the baseline. Each model \
            links to its full report.

            | Classifier | Prompt | Accuracy | Accuracy on clear rows | Answered UNCLASSIFIABLE | Agrees with baseline \
            | Cost per 100 | Run |
            |---|---|---|---|---|---|---|---|
            """;

    /** A summary row's classifier, without any link, and its prompt version. */
    private static final Pattern ROW = Pattern.compile("^\\| (?:\\[([^]]+)]\\([^)]*\\)|([^|]+?)) \\| ([^|]+?) \\|");

    private final ClassificationModel claude;
    private final JdbcClient jdbc;
    private final EvalProperties properties;
    private final Clock clock;

    /** @param claude the Claude client itself, the only model bean; the live path's guard wraps it only there */
    Evaluation(ClassificationModel claude, JdbcClient jdbc, EvalProperties properties, Clock clock) {
        this.claude = claude;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    EvalReport run() throws IOException {
        List<GoldSet.Row> gold = GoldSet.read(properties.goldSetFile());
        var budget = new EvalBudget(claude, properties.capUsd());
        Map<String, Classification> answers = new GroupClassifier(budget)
                .classify(gold.stream().map(row -> new Description(row.descriptionHash(), row.description())).toList())
                .stream()
                .collect(Collectors.toMap(Classification::descriptionHash, Function.identity()));
        Map<String, String> baselines = baselines(gold);
        var rows = gold.stream()
                .map(row -> {
                    Classification answer = answers.get(row.descriptionHash());
                    return new Scored(row, answer.category(), answer.reasonCode(), baselines.get(row.pscCode()));
                })
                .toList();
        var report = new EvalReport(budget.model(), budget.promptVersion(), clock.instant(), rows, budget.requests(),
                budget.refusedRequests(), budget.used(), budget.spent());
        write(report);
        return report;
    }

    /** The baseline's category for each PSC in the set, read through the same function the indexer uses. */
    private Map<String, String> baselines(List<GoldSet.Row> gold) {
        var baselines = new HashMap<String, String>();
        gold.stream().map(GoldSet.Row::pscCode).filter(Objects::nonNull).distinct().forEach(psc ->
                // single() rejects a null value, which a PSC the map doesn't cover returns.
                baselines.put(psc, jdbc.sql("SELECT psc_baseline_category(CAST(:psc AS text))")
                        .param("psc", psc)
                        .query(String.class)
                        .optional()
                        .orElse(null)));
        return baselines;
    }

    /** Writes the report, then puts its row and the baseline's in the summary, replacing any from an earlier run. */
    private void write(EvalReport report) throws IOException {
        Path reportFile = properties.reportDirectory().resolve(report.model() + "-" + report.promptVersion() + ".md");
        Files.createDirectories(properties.reportDirectory());
        Files.writeString(reportFile, report.markdown(), StandardCharsets.UTF_8);

        Path summary = properties.summaryFile();
        String link = summary.toAbsolutePath().getParent().relativize(reportFile.toAbsolutePath()).toString()
                .replace(File.separatorChar, '/');
        var rows = new ArrayList<String>();
        if (Files.exists(summary)) {
            boolean table = false;
            for (String line : Files.readAllLines(summary, StandardCharsets.UTF_8)) {
                if (line.startsWith("|---")) {
                    table = true;
                } else if (table && line.startsWith("| ")) {
                    rows.add(line);
                }
            }
        }
        put(rows, report.baselineSummaryRow());
        put(rows, report.summaryRow(link));
        Files.createDirectories(summary.toAbsolutePath().getParent());
        Files.writeString(summary, SUMMARY_HEAD + String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
    }

    /** Replaces the row for the same classifier and prompt version, or adds the row at the end. */
    private static void put(List<String> rows, String row) {
        String key = key(row);
        for (int i = 0; i < rows.size(); i++) {
            if (key(rows.get(i)).equals(key)) {
                rows.set(i, row);
                return;
            }
        }
        rows.add(row);
    }

    private static String key(String row) {
        Matcher matcher = ROW.matcher(row);
        if (!matcher.find()) {
            return row;
        }
        String classifier = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return classifier.strip() + " " + matcher.group(3).strip();
    }
}
