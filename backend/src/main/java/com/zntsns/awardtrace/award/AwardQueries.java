package com.zntsns.awardtrace.award;

import com.zntsns.awardtrace.award.internal.AwardRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Award reads for other modules. */
@Service
public class AwardQueries {

    /** An award and its newest live transactions; {@code truncated} means older ones were left out. */
    public record AwardWithTransactions(Award award, List<AwardTransaction> transactions, boolean truncated) {
    }

    /** @param latestSourceModifiedAt when USAspending last changed any of them; null when there are none */
    public record LiveAwards(long count, Instant latestSourceModifiedAt) {
    }

    private final AwardRepository awards;

    AwardQueries(AwardRepository awards) {
        this.awards = awards;
    }

    /**
     * Reads the award and its transactions from one snapshot, so a pipeline commit between the two queries can't
     * pair the award at one index version with transactions from the next. PostgreSQL's default, READ COMMITTED,
     * takes a new snapshot for every statement; REPEATABLE READ keeps the first one for the whole transaction.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<AwardWithTransactions> find(String awardId, int maxTransactions) {
        return awards.findLive(awardId).map(award -> {
            // One extra row shows whether any were left out.
            var transactions = awards.findLiveTransactions(awardId, Limit.of(maxTransactions + 1));
            boolean truncated = transactions.size() > maxTransactions;
            return new AwardWithTransactions(award,
                    List.copyOf(truncated ? transactions.subList(0, maxTransactions) : transactions), truncated);
        });
    }

    /** Counts every live award, so it scans the table; callers cache it. */
    @Transactional(readOnly = true)
    public LiveAwards liveAwards() {
        return awards.liveAwards();
    }
}
