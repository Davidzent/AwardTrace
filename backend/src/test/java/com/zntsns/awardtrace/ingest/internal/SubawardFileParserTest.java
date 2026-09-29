package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.ingest.SubawardReported;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Rejected;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Reported;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.SkipReason;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Skipped;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubawardFileParserTest {

    private final SubawardFileParser parser = new SubawardFileParser();

    @Test
    void reportsEachSubawardWithItsVersionAndPrimeAward() {
        List<ParsedRow> rows = parse(Fixtures.subawardCsv());

        assertThat(rows).hasSize(5);
        assertThat(rows.getFirst()).isEqualTo(new Reported(1, new SubawardReported(
                "AEA4804D-85C0-4592-8DB7-C211B603D4D1",
                Instant.parse("2025-10-24T00:00:00Z"),
                "CONT_AWD_1232SA25F0574_12H2_12305B24D0001_12H2",
                "C8XUKRPKN215",
                "ETTM JV LLC",
                "E2QCEKQXLN48",
                "DVORAK, LLC",
                "ARS-037002",
                new BigDecimal("70321.95"),
                LocalDate.of(2025, 10, 8),
                "FIRE PUMP CODE COMPLIANCE")));
    }

    @Test
    void keepsCorrectionsMultiLineDescriptionsAndSubawardsUnderAnIdv() {
        List<ParsedRow> rows = parse(Fixtures.subawardCsv());

        var correction = ((Reported) rows.get(1)).event();
        assertThat(correction.amount()).isEqualByComparingTo("-18933.00");
        assertThat(correction.description()).startsWith("\"WORK INCLUDED").contains("10 2600 WALL PROTECTION");
        assertThat(((Reported) rows.get(2)).event().primeAwardId()).isEqualTo("CONT_IDV_12760420A0002_12C2");
    }

    @Test
    void skipsActionsBeforeScopeAndRejectsUnreadableValues() {
        List<ParsedRow> rows = parse(Fixtures.subawardCsv());

        assertThat(rows.get(3)).isEqualTo(new Skipped(4, SkipReason.BEFORE_SCOPE));
        assertThat(rows.get(4)).isInstanceOf(Rejected.class).extracting(ParsedRow::rowNumber).isEqualTo(5L);
    }

    @Test
    void failsTheFileWhenAColumnIsMissing() {
        assertThatThrownBy(() -> parse("subaward_sam_report_id,prime_award_unique_key\nX,CONT_AWD_A\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no column subaward_action_date");
    }

    private List<ParsedRow> parse(String csv) {
        try (var rows = parser.parse(new StringReader(csv))) {
            return rows.toList();
        }
    }
}
