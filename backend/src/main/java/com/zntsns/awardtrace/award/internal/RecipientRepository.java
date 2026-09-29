package com.zntsns.awardtrace.award.internal;

import com.zntsns.awardtrace.award.Recipient;
import com.zntsns.awardtrace.award.RecipientProfile.FiscalYearRollup;
import com.zntsns.awardtrace.award.RecipientProfile.Rollup;
import com.zntsns.awardtrace.award.RecipientProfile.Totals;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Reads recipients and rollups over their live awards. Other modules read through
 * {@link com.zntsns.awardtrace.award.RecipientQueries}.
 */
public interface RecipientRepository extends Repository<Recipient, String> {

    /** A recipient whose awards are all deleted is kept by the pipeline but is no longer shown. */
    @Query("""
            SELECT r FROM Recipient r
            WHERE r.uei = :uei
              AND EXISTS (SELECT 1 FROM Award a WHERE a.recipient = r AND a.deletedAt IS NULL)
            """)
    Optional<Recipient> findLive(String uei);

    @Query("""
            SELECT new com.zntsns.awardtrace.award.RecipientProfile$Totals(
                count(a), sum(a.totalObligated), min(a.firstActionDate), max(a.lastActionDate))
            FROM Award a
            WHERE a.recipient.uei = :uei AND a.deletedAt IS NULL
            """)
    Totals totals(String uei);

    @Query("""
            SELECT new com.zntsns.awardtrace.award.RecipientProfile$Rollup(
                t.code, t.name, count(a), sum(a.totalObligated))
            FROM Award a JOIN a.awardingToptier t
            WHERE a.recipient.uei = :uei AND a.deletedAt IS NULL
            GROUP BY t.code, t.name
            ORDER BY sum(a.totalObligated) DESC, t.code
            """)
    List<Rollup> topAgencies(String uei, Limit limit);

    @Query("""
            SELECT new com.zntsns.awardtrace.award.RecipientProfile$Rollup(
                a.naicsCode, max(a.naicsDescription), count(a), sum(a.totalObligated))
            FROM Award a
            WHERE a.recipient.uei = :uei AND a.deletedAt IS NULL AND a.naicsCode IS NOT NULL
            GROUP BY a.naicsCode
            ORDER BY sum(a.totalObligated) DESC, a.naicsCode
            """)
    List<Rollup> topNaics(String uei, Limit limit);

    @Query("""
            SELECT new com.zntsns.awardtrace.award.RecipientProfile$FiscalYearRollup(
                a.fiscalYear, count(a), sum(a.totalObligated))
            FROM Award a
            WHERE a.recipient.uei = :uei AND a.deletedAt IS NULL
            GROUP BY a.fiscalYear
            ORDER BY a.fiscalYear
            """)
    List<FiscalYearRollup> byFiscalYear(String uei);
}
