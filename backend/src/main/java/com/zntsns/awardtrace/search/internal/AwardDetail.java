package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.AwardQueries.AwardWithTransactions;
import com.zntsns.awardtrace.award.AwardQueries.SubawardSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * The body of {@code GET /api/v1/awards/{award_id}} (doc 07). Until the classifier runs (Phase 5), the category is
 * the PSC baseline.
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
        CategoryDetail category,
        Period periodOfPerformance,
        Place placeOfPerformance,
        List<Modification> transactions,
        boolean transactionsTruncated,
        SubawardSummary subawardSummary,
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

    /**
     * The award's category beside the PSC baseline, so the reader can compare the two (doc 08).
     *
     * @param source {@code baseline} from the PSC, or {@code llm} once the classifier has run (doc 09)
     * @param confidence null, like {@code model} and {@code promptVersion}, when the source is {@code baseline}
     */
    record CategoryDetail(String code, String label, String source, Double confidence, String model,
            String promptVersion, String baselineCode, String baselineLabel) {
    }

    record Place(String stateCode, String countryCode) {
    }

    record Modification(String transactionId, String modificationNumber, LocalDate actionDate,
            BigDecimal federalActionObligation, String description) {
    }

    /** @param labels the taxonomy's label for a category code */
    static AwardDetail of(AwardWithTransactions found, Function<String, String> labels) {
        var award = found.award();
        String baseline = award.baselineCategory();
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
                baseline == null ? null : new CategoryDetail(baseline, labels.apply(baseline), "baseline", null, null,
                        null, baseline, labels.apply(baseline)),
                new Period(award.popStartDate(), award.popEndDate()),
                new Place(award.popStateCode(), award.popCountryCode()),
                found.transactions().stream()
                        .map(transaction -> new Modification(transaction.transactionId(),
                                transaction.modificationNumber(), transaction.actionDate(),
                                transaction.federalActionObligation(), transaction.description()))
                        .toList(),
                found.truncated(),
                found.subawards(),
                award.sourceModifiedAt(),
                "https://www.usaspending.gov/award/" + award.awardId() + "/");
    }
}
