package com.zntsns.awardtrace.award;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Who a recipient works with, from reported subawards only (ADR 0014): the primes that reported subawards to it, and
 * the subrecipients it reported subawards to. Each list is largest total first.
 */
public record RecipientNetwork(List<Partner> primesAbove, List<Partner> subsBelow) {

    /**
     * @param name as most recently reported
     * @param totalAmount the sum of the reported amounts, which corrections can lower
     * @param hasAwards whether the partner holds live awards here, and so has a recipient profile; a subrecipient or
     *     an out-of-scope prime may not
     */
    public record Partner(String uei, String name, long subawardCount, BigDecimal totalAmount,
            LocalDate firstActionDate, LocalDate lastActionDate, boolean hasAwards) {
    }
}
