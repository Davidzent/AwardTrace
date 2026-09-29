package com.zntsns.awardtrace.award;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A recipient and rollups over its live awards. Top lists rank by total obligated, largest first. */
public record RecipientProfile(
        Recipient recipient,
        Totals totals,
        List<Rollup> topAgencies,
        List<Rollup> topNaics,
        List<FiscalYearRollup> byFiscalYear) {

    public record Totals(long awardCount, BigDecimal totalObligated, LocalDate firstActionDate,
            LocalDate lastActionDate) {
    }

    /** The awards under one awarding agency or NAICS code, with its name. */
    public record Rollup(String code, String name, long awardCount, BigDecimal totalObligated) {
    }

    public record FiscalYearRollup(short fiscalYear, long awardCount, BigDecimal totalObligated) {
    }
}
