package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.RecipientProfile;
import com.zntsns.awardtrace.award.RecipientProfile.FiscalYearRollup;
import com.zntsns.awardtrace.award.RecipientProfile.Rollup;
import com.zntsns.awardtrace.search.internal.AwardDetail.RecipientRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The body of {@code GET /api/v1/recipients/{uei}} (doc 07). The first and last action dates span the recipient's
 * live awards.
 */
record RecipientDetail(
        String uei,
        String name,
        RecipientRef parent,
        Location location,
        Totals totals,
        List<Rollup> topAgencies,
        List<Rollup> topNaics,
        List<FiscalYearRollup> awardsByFiscalYear,
        LocalDate firstActionDate,
        LocalDate lastActionDate) {

    record Location(String city, String stateCode, String countryCode) {
    }

    record Totals(long awardCount, BigDecimal totalObligated) {
    }

    static RecipientDetail of(RecipientProfile profile) {
        var recipient = profile.recipient();
        var totals = profile.totals();
        return new RecipientDetail(
                recipient.uei(),
                recipient.name(),
                recipient.parentUei() == null ? null : new RecipientRef(recipient.parentUei(), recipient.parentName()),
                new Location(recipient.city(), recipient.stateCode(), recipient.countryCode()),
                new Totals(totals.awardCount(), totals.totalObligated()),
                profile.topAgencies(),
                profile.topNaics(),
                profile.byFiscalYear(),
                totals.firstActionDate(),
                totals.lastActionDate());
    }
}
