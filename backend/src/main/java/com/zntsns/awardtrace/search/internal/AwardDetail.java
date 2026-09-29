package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.AwardQueries.AwardWithTransactions;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The body of {@code GET /api/v1/awards/{award_id}} (doc 07). Category and subaward fields arrive with the phases
 * that produce their data.
 */
record AwardDetail(
        String awardId,
        String piid,
        String parentPiid,
        String awardType,
        String description,
        RecipientRef recipient,
        AgencyRef agency,
        FundingAgencyRef fundingAgency,
        BigDecimal totalObligated,
        BigDecimal potentialTotalValue,
        LocalDate lastActionDate,
        int fiscalYear,
        String naicsCode,
        String popStateCode,
        Code naics,
        Code psc,
        Period periodOfPerformance,
        Place placeOfPerformance,
        List<Modification> transactions,
        boolean transactionsTruncated,
        Instant sourceModifiedAt,
        String usaspendingUrl) {

    record RecipientRef(String uei, String name) {
    }

    record AgencyRef(String code, String name, String subtierName) {
    }

    record FundingAgencyRef(String code, String name) {
    }

    record Code(String code, String description) {
    }

    record Period(LocalDate start, LocalDate end) {
    }

    record Place(String stateCode, String countryCode) {
    }

    record Modification(String transactionId, String modificationNumber, LocalDate actionDate,
            BigDecimal federalActionObligation, String description) {
    }

    static AwardDetail of(AwardWithTransactions found) {
        var award = found.award();
        var subtier = award.awardingSubtier();
        var funding = award.fundingToptier();
        return new AwardDetail(
                award.awardId(),
                award.piid(),
                award.parentPiid(),
                award.awardType(),
                award.description(),
                new RecipientRef(award.recipient().uei(), award.recipient().name()),
                new AgencyRef(award.awardingToptier().code(), award.awardingToptier().name(),
                        subtier == null ? null : subtier.name()),
                funding == null ? null : new FundingAgencyRef(funding.code(), funding.name()),
                award.totalObligated(),
                award.potentialTotalValue(),
                award.lastActionDate(),
                award.fiscalYear(),
                award.naicsCode(),
                award.popStateCode(),
                new Code(award.naicsCode(), award.naicsDescription()),
                new Code(award.pscCode(), award.pscDescription()),
                new Period(award.popStartDate(), award.popEndDate()),
                new Place(award.popStateCode(), award.popCountryCode()),
                found.transactions().stream()
                        .map(transaction -> new Modification(transaction.transactionId(),
                                transaction.modificationNumber(), transaction.actionDate(),
                                transaction.federalActionObligation(), transaction.description()))
                        .toList(),
                found.truncated(),
                award.sourceModifiedAt(),
                "https://www.usaspending.gov/award/" + award.awardId() + "/");
    }
}
