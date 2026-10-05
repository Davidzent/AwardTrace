package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Four labeled rows, each answered NATURAL_RESOURCES by {@link FakeClaude}. The baseline reads PSC F003 as
 * NATURAL_RESOURCES, R408 as PROFESSIONAL_SERVICES, and Z2NB as CONSTRUCTION_FACILITIES; the vague row has no PSC.
 */
@SpringBootTest
@ActiveProfiles("enricher")
@Import({TestcontainersConfiguration.class, FakeClaudeConfiguration.class})
class EvaluationIT {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T15:04:00Z"), ZoneOffset.UTC);

    @Autowired
    ClassificationModel claude;

    @Autowired
    FakeClaude fakeClaude;

    @Autowired
    JdbcClient jdbc;

    @TempDir
    Path dir;

    private Path gold;

    @BeforeEach
    void writeGoldSet() throws IOException {
        fakeClaude.answer("NATURAL_RESOURCES");
        gold = Files.writeString(Files.createDirectories(dir.resolve("eval")).resolve("gold.csv"), """
                description_hash,description,psc_code,label
                %s,"ENGINE TYPE 6 WITH CREW, CEDAR CREEK",F003,NATURAL_RESOURCES
                %s,PROGRAM MANAGEMENT SUPPORT,R408,PROFESSIONAL_SERVICES
                %s,"REROOF | HVAC, ASHLAND RANGER STATION",Z2NB,CONSTRUCTION_FACILITIES
                %s,SEE SCHEDULE,,UNCLASSIFIABLE
                """.formatted("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64)));
    }

    @AfterEach
    void resetClaude() {
        fakeClaude.reset();
    }

    @Test
    void scoresTheModelAndTheBaselineAndWritesTheReportAndSummary() throws IOException {
        EvalReport report = evaluation(new BigDecimal("1.00")).run();

        assertThat(fakeClaude.requests()).containsExactly(
                List.of("aaaaaaaaaaaa", "bbbbbbbbbbbb", "cccccccccccc", "dddddddddddd"));
        assertThat(report.modelCorrect(false)).isEqualTo(1);
        assertThat(report.baselineCorrect(false)).isEqualTo(3);
        assertThat(Files.readString(dir.resolve("eval/results/claude-haiku-4-5-v1.md")))
                .contains("| Accuracy | 25.0% (1 of 4) | 75.0% (3 of 4) |")
                .contains("| Accuracy on rows not labeled UNCLASSIFIABLE | 33.3% (1 of 3) | 100.0% (3 of 3) |");
        assertThat(Files.readString(dir.resolve("docs/results.md")))
                .startsWith("# Classification results")
                .contains("| PSC baseline | - | 75.0% | 100.0% | 0.0% | - | Free | 2026-10-06 |")
                .contains("| [claude-haiku-4-5](../eval/results/claude-haiku-4-5-v1.md) | v1 | 25.0% | 33.3% |");
        // The run writes no classification and spends nothing on the live path's ledger.
        assertThat(jdbc.sql("SELECT count(*) FROM classification").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM enrichment_spend").query(Long.class).single()).isZero();
    }

    @Test
    void replacesARunsSummaryRowWhenTheSameModelAndPromptRunAgain() throws IOException {
        evaluation(new BigDecimal("1.00")).run();
        evaluation(new BigDecimal("1.00")).run();

        assertThat(Files.readAllLines(dir.resolve("docs/results.md")))
                .filteredOn(line -> line.startsWith("| PSC baseline") || line.startsWith("| [claude-haiku-4-5]"))
                .hasSize(2);
    }

    @Test
    void sendsNothingWhenARequestCouldPassTheRunsCap() {
        assertThatThrownBy(() -> evaluation(new BigDecimal("0.01")).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cap of $0.01");

        assertThat(fakeClaude.requests()).isEmpty();
        assertThat(dir.resolve("docs/results.md")).doesNotExist();
    }

    private Evaluation evaluation(BigDecimal cap) {
        var properties = new EvalProperties(gold.toString(), dir.resolve("eval/results").toString(),
                dir.resolve("docs/results.md").toString(), cap);
        return new Evaluation(claude, jdbc, properties, CLOCK);
    }
}
