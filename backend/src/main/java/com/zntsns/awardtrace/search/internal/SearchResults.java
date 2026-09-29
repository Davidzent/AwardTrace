package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.search.internal.AwardDetail.AgencyRef;
import com.zntsns.awardtrace.search.internal.AwardDetail.RecipientRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The body of {@code GET /api/v1/awards/search} (doc 07). Facets, categories, and subaward counts arrive with the
 * commits and phases that produce them.
 *
 * @param totalObligated the sum over every match, not just this page
 * @param totalIsCapped true when {@code total} is a lower bound
 */
record SearchResults(
        long total,
        boolean totalIsCapped,
        BigDecimal totalObligated,
        long tookMs,
        int page,
        int size,
        List<Result> results) {

    /** @param descriptionHighlight the description with matches wrapped in {@code <mark>}, around HTML-escaped text */
    record Result(
            String awardId,
            String piid,
            String description,
            String descriptionHighlight,
            RecipientRef recipient,
            AgencyRef agency,
            BigDecimal totalObligated,
            LocalDate lastActionDate,
            Integer fiscalYear,
            String naicsCode,
            String popStateCode) {
    }
}
