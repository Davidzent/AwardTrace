package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.search.internal.AwardDetail.AgencyRef;
import com.zntsns.awardtrace.search.internal.AwardDetail.RecipientRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The body of {@code GET /api/v1/awards/search} (doc 07).
 *
 * @param totalObligated the sum over every match, not just this page
 * @param totalIsCapped true when {@code total} is a lower bound
 * @param facets for {@code agency}, {@code category}, {@code state}, {@code naics}, and {@code fiscal_year}, the most
 *     common values, each counted under every selection but its facet's own
 */
record SearchResults(
        long total,
        boolean totalIsCapped,
        BigDecimal totalObligated,
        long tookMs,
        int page,
        int size,
        List<Result> results,
        Map<String, List<FacetValue>> facets) {

    /** @param label a display name, where the facet has one: agency names, category labels, and NAICS descriptions */
    record FacetValue(String value, String label, long count) {
    }

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
            CategoryRef category,
            String popStateCode,
            int subawardCount) {
    }

    /** @param source {@code baseline} from the PSC, or {@code llm} once the classifier has run (doc 09) */
    record CategoryRef(String code, String label, String source) {
    }
}
