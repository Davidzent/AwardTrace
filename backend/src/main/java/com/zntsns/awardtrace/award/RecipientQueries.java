package com.zntsns.awardtrace.award;

import com.zntsns.awardtrace.award.internal.RecipientRepository;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Recipient reads for other modules. */
@Service
public class RecipientQueries {

    private final RecipientRepository recipients;

    RecipientQueries(RecipientRepository recipients) {
        this.recipients = recipients;
    }

    /**
     * Reads the recipient and every rollup from one snapshot, so the totals and the lists always describe the same
     * awards. See {@link AwardQueries#find} for why that takes REPEATABLE READ.
     *
     * @param top how many agencies and NAICS codes to list
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<RecipientProfile> find(String uei, int top) {
        return recipients.findLive(uei).map(recipient -> new RecipientProfile(recipient,
                recipients.totals(uei),
                recipients.topAgencies(uei, Limit.of(top)),
                recipients.topNaics(uei, Limit.of(top)),
                recipients.byFiscalYear(uei)));
    }
}
