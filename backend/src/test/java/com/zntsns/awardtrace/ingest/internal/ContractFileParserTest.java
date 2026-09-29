package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Deleted;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Ingested;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Rejected;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.SkipReason;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Skipped;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fixtures are rows cut from the Department of Agriculture's 2026-09 full and delta files. */
class ContractFileParserTest {

    private static final String FULL_FILE = "FY2026_012_Contracts_Full_20260909_1.csv";
    private static final String DELTA_FILE = "FY(All)_012_Contracts_Delta_20260908_1.csv";

    private final ContractFileParser parser = new ContractFileParser();

    @Test
    void mapsAFullFileRowToAnEvent() {
        ContractTransactionIngested base = ((Ingested) parse(fixture(FULL_FILE), FULL_FILE).getFirst()).event();

        assertThat(base.transactionId()).isEqualTo("12C2_12C2_12024B26M0522_0_12024B24T7051_0");
        assertThat(base.awardId()).isEqualTo("CONT_AWD_12024B26M0522_12C2_12024B24T7051_12C2");
        assertThat(base.sourceFile()).isEqualTo(FULL_FILE);
        assertThat(base.sourceFileDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(base.sourceModifiedAt()).isEqualTo(Instant.parse("2026-08-17T23:02:10Z"));
        assertThat(base.piid()).isEqualTo("12024B26M0522");
        assertThat(base.parentPiid()).isEqualTo("12024B24T7051");
        assertThat(base.modificationNumber()).isEqualTo("0");
        assertThat(base.awardType()).isEqualTo("C");
        assertThat(base.actionTypeCode()).isNull();
        assertThat(base.actionDate()).isEqualTo(LocalDate.of(2026, 7, 16));
        assertThat(base.federalActionObligation()).isEqualTo(new BigDecimal("27500.00"));
        assertThat(base.totalObligated()).isEqualTo(new BigDecimal("27500.00"));
        assertThat(base.popEndDate()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(base.awardingToptierCode()).isEqualTo("012");
        assertThat(base.awardingSubtierCode()).isEqualTo("12C2");
        assertThat(base.recipientUei()).isEqualTo("MN5KRX2W9R46");
        assertThat(base.recipientCountryCode()).isEqualTo("USA");
        assertThat(base.popStateCode()).isEqualTo("ID");
        assertThat(base.naicsCode()).isEqualTo("517810");
        assertThat(base.pscCode()).isEqualTo("F003");
        assertThat(base.awardDescription()).isEqualTo("NOMADIC LAND CAMPS, LLC IDIPF000347 E40");
    }

    @Test
    void keepsNegativeObligations() {
        ContractTransactionIngested deobligation = ((Ingested) parse(fixture(FULL_FILE), FULL_FILE).get(1)).event();

        assertThat(deobligation.modificationNumber()).isEqualTo("P00002");
        assertThat(deobligation.federalActionObligation()).isEqualTo(new BigDecimal("-27500.00"));
    }

    @Test
    void skipsIdvRows() {
        List<ParsedRow> rows = parse(fixture(FULL_FILE), FULL_FILE);

        assertThat(rows).hasSize(7);
        assertThat(rows).filteredOn(Ingested.class::isInstance).hasSize(5);
        assertThat(rows.subList(5, 7)).containsExactly(new Skipped(6, SkipReason.IDV), new Skipped(7, SkipReason.IDV));
    }

    @Test
    void turnsDeltaDeleteRowsIntoDeleteEventsWithTheirAwardKey() {
        List<ParsedRow> rows = parse(fixture(DELTA_FILE), DELTA_FILE);

        assertThat(rows).filteredOn(Deleted.class::isInstance)
                .extracting(row -> ((Deleted) row).event())
                .containsExactly(
                        new ContractTransactionDeleted("12K3_-NONE-_12639526P0220_0_-NONE-_0",
                                "CONT_AWD_12639526P0220_12K3_-NONE-_-NONE-", DELTA_FILE, LocalDate.of(2026, 9, 8)),
                        new ContractTransactionDeleted("12K2_12K2_123J1426F0908_P00001_123J1426D0005_0",
                                "CONT_AWD_123J1426F0908_12K2_123J1426D0005_12K2", DELTA_FILE,
                                LocalDate.of(2026, 9, 8)));
    }

    @Test
    void skipsActionsBeforeFiscalYear2025() {
        List<ParsedRow> rows = parse(fixture(DELTA_FILE), DELTA_FILE);

        assertThat(rows).filteredOn(Skipped.class::isInstance)
                .containsExactly(
                        new Skipped(4, SkipReason.BEFORE_SCOPE),
                        new Skipped(5, SkipReason.BEFORE_SCOPE),
                        new Skipped(6, SkipReason.IDV));
        assertThat(((Ingested) rows.getFirst()).event().actionDate()).isEqualTo(LocalDate.of(2025, 10, 1));
    }

    @Test
    void derivesTheSameAwardKeyTheSourcePublishes() {
        var ingested = List.of(FULL_FILE, DELTA_FILE).stream()
                .flatMap(file -> parse(fixture(file), file).stream())
                .filter(Ingested.class::isInstance)
                .map(row -> ((Ingested) row).event())
                .toList();

        assertThat(ingested).hasSize(6)
                .allSatisfy(event -> assertThat(ContractFileParser.awardIdOf(event.transactionId()))
                        .isEqualTo(event.awardId()));
    }

    @Test
    void rejectsARowWithAnUnreadableValueAndKeepsGoing() {
        String csv = fixture(FULL_FILE).replace("2026-08-17 23:02:10+00", "2026-08-17T23:02:10");

        List<ParsedRow> rows = parse(csv, FULL_FILE);

        assertThat(rows.getFirst()).isInstanceOf(Rejected.class);
        assertThat(rows.get(1)).isInstanceOf(Ingested.class);
    }

    @Test
    void failsTheFileWhenAColumnIsMissing() {
        String csv = fixture(FULL_FILE).replaceFirst("recipient_uei", "recipient_id");

        assertThatThrownBy(() -> parse(csv, FULL_FILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source file has no column recipient_uei");
    }

    private List<ParsedRow> parse(String csv, String fileName) {
        try (var rows = parser.parse(new StringReader(csv), fileName)) {
            return rows.toList();
        }
    }

    private static String fixture(String fileName) {
        try (var in = ContractFileParserTest.class.getResourceAsStream("/fixtures/contracts/" + fileName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
