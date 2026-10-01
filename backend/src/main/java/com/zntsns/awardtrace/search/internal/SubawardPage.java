package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.Subaward;
import com.zntsns.awardtrace.search.internal.AwardDetail.RecipientRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The body of {@code GET /api/v1/awards/{award_id}/subawards} (doc 07).
 *
 * @param totalIsCapped always false: PostgreSQL counts exactly
 */
record SubawardPage(long total, boolean totalIsCapped, int page, int size, List<SubawardItem> results) {

    /**
     * @param subRecipient its UEI is null when the subrecipient isn't registered in SAM.gov
     * @param amount negative for a correction that lowers an earlier report
     */
    record SubawardItem(String subawardKey, RecipientRef subRecipient, BigDecimal amount, LocalDate actionDate,
            String description) {
    }

    static SubawardPage of(Page<Subaward> found) {
        return new SubawardPage(found.getTotalElements(), false, found.getNumber() + 1, found.getSize(),
                found.stream()
                        .map(subaward -> new SubawardItem(subaward.subawardKey(),
                                new RecipientRef(subaward.subRecipientUei(), subaward.subRecipientName()),
                                subaward.amount(), subaward.actionDate(), subaward.description()))
                        .toList());
    }
}
