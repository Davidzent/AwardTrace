package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.ingest.SubawardReported;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Transaction events for one Department of Agriculture award, shaped like real delta and full file rows. */
final class TestEvents {

    static final String AWARD_ID = "CONT_AWD_12024B26M0522_12C2_12024B24T7051_12C2";

    private TestEvents() {
    }

    /**
     * A subaward under {@link #AWARD_ID}'s prime recipient, reported at {@code modifiedAt}, such as
     * {@code 2026-08-01}.
     */
    static SubawardReported subaward(String key, String primeAwardId, String subUei, String subName, String amount,
            String modifiedAt) {
        return new SubawardReported(key, Instant.parse(modifiedAt + "T00:00:00Z"), primeAwardId, "MN5KRX2W9R46",
                "NOMADIC LAND CAMPS, LLC", subUei, subName, "SUB-" + key, new BigDecimal(amount),
                LocalDate.parse(modifiedAt), "Tent rental");
    }

    /** A source file generated on {@code yyyymmdd}, which versions every row it holds. */
    static String file(String yyyymmdd) {
        return "FY2026_012_Contracts_Full_" + yyyymmdd + "_1.csv";
    }

    static ContractTransactionIngested transaction(String modification, String actionDate, String obligation,
            String totalObligated, String sourceFile) {
        return transaction(AWARD_ID, modification, actionDate, obligation, totalObligated, sourceFile);
    }

    static ContractTransactionIngested transaction(String awardId, String modification, String actionDate,
            String obligation, String totalObligated, String sourceFile) {
        return new ContractTransactionIngested(
                transactionId(awardId, modification),
                awardId,
                sourceFile,
                fileDate(sourceFile),
                Instant.parse(actionDate + "T12:00:00Z"),
                "12024B26M0522",
                "12024B24T7051",
                modification,
                "C",
                null,
                LocalDate.parse(actionDate),
                new BigDecimal(obligation),
                new BigDecimal(totalObligated),
                new BigDecimal(totalObligated),
                LocalDate.of(2026, 7, 16),
                LocalDate.of(2026, 8, 6),
                "012",
                "Department of Agriculture",
                "12C2",
                "Forest Service",
                "012",
                "Department of Agriculture",
                "MN5KRX2W9R46",
                "NOMADIC LAND CAMPS, LLC",
                "MN5KRX2W9R46",
                "NOMADIC LAND CAMPS, LLC",
                "BOISE",
                "ID",
                "USA",
                "ID",
                "USA",
                "517810",
                "ALL OTHER TELECOMMUNICATIONS",
                "F003",
                "NATURAL RESOURCES/CONSERVATION- FOREST-RANGE FIRE SUPPRESSION/PRESUPPRESSION",
                "MOD " + modification,
                "NOMADIC LAND CAMPS, LLC IDIPF000347 E40");
    }

    static ContractTransactionDeleted deletion(String modification, String sourceFile) {
        return new ContractTransactionDeleted(transactionId(AWARD_ID, modification), AWARD_ID, sourceFile,
                fileDate(sourceFile));
    }

    private static String transactionId(String awardId, String modification) {
        return awardId.replace("CONT_AWD_", "") + "_" + modification;
    }

    private static LocalDate fileDate(String sourceFile) {
        String yyyymmdd = sourceFile.replaceAll(".*_(\\d{8})_\\d+\\.csv$", "$1");
        return LocalDate.parse(yyyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
    }
}
