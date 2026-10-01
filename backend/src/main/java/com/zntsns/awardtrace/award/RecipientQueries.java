package com.zntsns.awardtrace.award;

import com.zntsns.awardtrace.award.RecipientNetwork.Partner;
import com.zntsns.awardtrace.award.internal.RecipientRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Recipient reads for other modules. */
@Service
public class RecipientQueries {

    // recipient_edge is a materialized view the pipeline refreshes (ADR 0014), so it is read with SQL, not mapped.
    private static final String PRIMES_ABOVE = """
            SELECT prime_uei AS uei, prime_name AS name, subaward_count, total_amount, first_action_date,
                   last_action_date
            FROM recipient_edge
            WHERE sub_uei = :uei
            ORDER BY total_amount DESC, prime_uei
            LIMIT :limit
            """;

    private static final String SUBS_BELOW = """
            SELECT sub_uei AS uei, sub_name AS name, subaward_count, total_amount, first_action_date,
                   last_action_date
            FROM recipient_edge
            WHERE prime_uei = :uei
            ORDER BY total_amount DESC, sub_uei
            LIMIT :limit
            """;

    private final RecipientRepository recipients;
    private final JdbcClient jdbc;

    RecipientQueries(RecipientRepository recipients, JdbcClient jdbc) {
        this.recipients = recipients;
        this.jdbc = jdbc;
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

    /**
     * Both sides from one snapshot. A UEI that never appears in a subaward has an empty network, not a missing one.
     *
     * @param limit how many partners to list on each side
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RecipientNetwork network(String uei, int limit) {
        return new RecipientNetwork(partners(PRIMES_ABOVE, uei, limit), partners(SUBS_BELOW, uei, limit));
    }

    private List<Partner> partners(String sql, String uei, int limit) {
        return jdbc.sql(sql).param("uei", uei).param("limit", limit).query(Partner.class).list();
    }
}
