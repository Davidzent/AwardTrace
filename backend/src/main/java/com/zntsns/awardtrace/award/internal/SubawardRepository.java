package com.zntsns.awardtrace.award.internal;

import com.zntsns.awardtrace.award.AwardQueries.SubawardSummary;
import com.zntsns.awardtrace.award.Subaward;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Reads the subawards reported under a prime award. Other modules read through
 * {@link com.zntsns.awardtrace.award.AwardQueries}.
 */
public interface SubawardRepository extends Repository<Subaward, String> {

    @Query("""
            SELECT new com.zntsns.awardtrace.award.AwardQueries$SubawardSummary(count(s), sum(s.amount))
            FROM Subaward s
            WHERE s.primeAwardId = :awardId
            """)
    SubawardSummary summary(String awardId);

    /** Newest first. */
    @Query("""
            SELECT s FROM Subaward s
            WHERE s.primeAwardId = :awardId
            ORDER BY s.actionDate DESC, s.subawardKey
            """)
    Page<Subaward> findByPrimeAward(String awardId, Pageable pageable);
}
