package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import com.zntsns.awardtrace.enrichment.internal.EvalReport.Scored;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalReportTest {

    /**
     * Five rows: the model gets the first and the vague one right; the baseline gets the first three right and has
     * no answer for the vague one, whose award has no PSC.
     */
    private final EvalReport report = new EvalReport("claude-haiku-4-5", "v1", Instant.parse("2026-10-06T15:04:00Z"),
            List.of(
                    scored("ENGINE TYPE 6 WITH CREW", "NATURAL_RESOURCES", "NATURAL_RESOURCES", null,
                            "NATURAL_RESOURCES"),
                    scored("PROGRAM MANAGEMENT SUPPORT", "PROFESSIONAL_SERVICES", "OTHER", null,
                            "PROFESSIONAL_SERVICES"),
                    scored("REROOF | HVAC, RANGER STATION", "CONSTRUCTION_FACILITIES", "UNCLASSIFIABLE", "REFUSAL",
                            "CONSTRUCTION_FACILITIES"),
                    scored("SEE SCHEDULE", "UNCLASSIFIABLE", "UNCLASSIFIABLE", "VAGUE", null),
                    scored("HAZARDOUS FUELS REDUCTION", "NATURAL_RESOURCES", "OTHER", null, "OTHER")),
            2, 1, new Usage(1_950, 150, 0, 0), new BigDecimal("0.0027"));

    @Test
    void scoresTheModelAndTheBaselineOverAllRowsAndOverClearOnes() {
        assertThat(report.modelCorrect(false)).isEqualTo(2);
        assertThat(report.baselineCorrect(false)).isEqualTo(3);
        assertThat(report.clearRows()).isEqualTo(4);
        assertThat(report.modelCorrect(true)).isEqualTo(1);
        assertThat(report.baselineCorrect(true)).isEqualTo(3);
        assertThat(report.agreement()).isEqualTo(2);
    }

    @Test
    void computesPrecisionAndRecallPerCategory() {
        // The model answered OTHER twice, wrongly both times; OTHER labels none, so its recall has no rows.
        assertThat(report.modelPrecision("OTHER")).isZero();
        assertThat(report.modelRecall("OTHER")).isNull();
        assertThat(report.modelRecall("NATURAL_RESOURCES")).isEqualTo(0.5);
        assertThat(report.modelPrecision("UNCLASSIFIABLE")).isEqualTo(0.5);
        assertThat(report.baselinePrecision("NATURAL_RESOURCES")).isEqualTo(1.0);
        assertThat(report.baselineRecall("NATURAL_RESOURCES")).isEqualTo(0.5);
        assertThat(report.modelPrecision("DEFENSE_SYSTEMS")).isNull();
        assertThat(report.confused("NATURAL_RESOURCES", "OTHER")).isEqualTo(1);
    }

    @Test
    void writesTheScoresUsageAndEveryMiss() {
        String markdown = report.markdown();

        assertThat(markdown)
                .startsWith("# claude-haiku-4-5, prompt v1\n")
                .contains("classified on 2026-10-06 at 15:04 UTC")
                .contains("| Accuracy | 40.0% (2 of 5) | 60.0% (3 of 5) |")
                .contains("| Accuracy on rows not labeled UNCLASSIFIABLE | 25.0% (1 of 4) | 75.0% (3 of 4) |")
                .contains("| Answered UNCLASSIFIABLE | 40.0% (2 of 5); the label agreed for 1 | Never |")
                .contains("| Refused | 1 request, 1 description | - |")
                .contains("| Agreement with the baseline | 40.0% (2 of 5) | - |")
                .contains("| Input tokens | 1,950 |")
                .contains("| Cost per 100 descriptions | $0.0540 |")
                .contains("| NATURAL_RESOURCES | 2 | 100.0% | 50.0% | 100.0% | 50.0% |")
                .contains("| Label | ITS | ITI | CYB |")
                .contains("| REROOF \\| HVAC, RANGER STATION | CONSTRUCTION_FACILITIES | UNCLASSIFIABLE (REFUSAL) "
                        + "| CONSTRUCTION_FACILITIES |")
                .doesNotContain("| ENGINE TYPE 6 WITH CREW |");
    }

    @Test
    void summarizesTheRunAndTheBaselineInOneRowEach() {
        assertThat(report.summaryRow("../eval/results/claude-haiku-4-5-v1.md")).isEqualTo(
                "| [claude-haiku-4-5](../eval/results/claude-haiku-4-5-v1.md) | v1 | 40.0% | 25.0% | 40.0% | 40.0% "
                        + "| $0.0540 | 2026-10-06 |");
        assertThat(report.baselineSummaryRow())
                .isEqualTo("| PSC baseline | - | 60.0% | 75.0% | 0.0% | - | Free | 2026-10-06 |");
    }

    private static Scored scored(String description, String label, String answer, String reason, String baseline) {
        return new Scored(new GoldSet.Row("0".repeat(64), description, null, label), answer, reason, baseline);
    }
}
