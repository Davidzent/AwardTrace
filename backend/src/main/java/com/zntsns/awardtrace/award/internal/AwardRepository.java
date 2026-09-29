package com.zntsns.awardtrace.award.internal;

import com.zntsns.awardtrace.award.Award;
import com.zntsns.awardtrace.award.AwardTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Reads awards that are not deleted. Deleted awards are kept only so the index can drop them (ADR 0012). Other
 * modules read through {@link com.zntsns.awardtrace.award.AwardQueries}.
 */
public interface AwardRepository extends Repository<Award, String> {

    /** The award with its recipient and agencies, fetched in one query. */
    @Query("""
            SELECT a FROM Award a
            JOIN FETCH a.recipient
            JOIN FETCH a.awardingToptier
            LEFT JOIN FETCH a.awardingSubtier
            LEFT JOIN FETCH a.fundingToptier
            WHERE a.awardId = :awardId AND a.deletedAt IS NULL
            """)
    Optional<Award> findLive(String awardId);

    /** Live modifications, newest first in action order. */
    @Query("""
            SELECT t FROM AwardTransaction t
            WHERE t.awardId = :awardId AND t.deletedAt IS NULL
            ORDER BY t.actionDate DESC, t.modificationNumber DESC, t.transactionId DESC
            """)
    List<AwardTransaction> findLiveTransactions(String awardId, Limit limit);
}
