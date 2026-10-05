package com.zntsns.awardtrace.award;

import com.zntsns.awardtrace.award.internal.AwardRepository;
import com.zntsns.awardtrace.award.internal.SubawardRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Award reads for other modules. */
@Service
public class AwardQueries {

    /**
     * An award and its newest live transactions; {@code truncated} means older ones were left out.
     *
     * @param classification the classifier's category for the award's description; null until it has one (doc 09)
     */
    public record AwardWithTransactions(Award award, List<AwardTransaction> transactions, boolean truncated,
            SubawardSummary subawards, ModelCategory classification) {
    }

    /**
     * The classifier's category for a description (doc 09).
     *
     * @param confidence the model's confidence, from 0 to 1; null for a refusal or a failure
     * @param reasonCode why the category is UNCLASSIFIABLE: VAGUE, REFUSAL, or FAILED; null for every other category
     * @param model the exact model ID that answered
     */
    public record ModelCategory(String category, BigDecimal confidence, String reasonCode, String model,
            String promptVersion) {
    }

    /** @param total the sum of the reported amounts, which corrections can lower */
    public record SubawardSummary(long count, BigDecimal total) {

        public SubawardSummary {
            // sum() over no rows is null; money always has two decimal places.
            total = total == null ? BigDecimal.ZERO.setScale(2) : total;
        }
    }

    /** @param latestSourceModifiedAt when USAspending last changed any of them; null when there are none */
    public record LiveAwards(long count, Instant latestSourceModifiedAt) {
    }

    private final AwardRepository awards;
    private final SubawardRepository subawards;
    private final JdbcClient jdbc;

    AwardQueries(AwardRepository awards, SubawardRepository subawards, JdbcClient jdbc) {
        this.awards = awards;
        this.subawards = subawards;
        this.jdbc = jdbc;
    }

    /**
     * Reads the award, its transactions, its subaward summary, and its classification from one snapshot, so a commit
     * between the queries can't pair the award at one index version with rows from the next. PostgreSQL's
     * default, READ COMMITTED, takes a new snapshot for every statement; REPEATABLE READ keeps the first one for the
     * whole transaction.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<AwardWithTransactions> find(String awardId, int maxTransactions) {
        return awards.findLive(awardId).map(award -> {
            // One extra row shows whether any were left out.
            var transactions = awards.findLiveTransactions(awardId, Limit.of(maxTransactions + 1));
            boolean truncated = transactions.size() > maxTransactions;
            return new AwardWithTransactions(award,
                    List.copyOf(truncated ? transactions.subList(0, maxTransactions) : transactions), truncated,
                    subawards.summary(awardId), classification(award.descriptionHash()));
        });
    }

    private ModelCategory classification(String descriptionHash) {
        if (descriptionHash == null) {
            return null;
        }
        return jdbc.sql("""
                        SELECT category, confidence, reason_code, model, prompt_version
                        FROM classification WHERE description_hash = :hash
                        """)
                .param("hash", descriptionHash)
                .query(ModelCategory.class)
                .optional()
                .orElse(null);
    }

    /**
     * A page of the award's reported subawards, newest first; empty when the award isn't live.
     *
     * @param page the first page is 1
     */
    @Transactional(readOnly = true)
    public Optional<Page<Subaward>> subawards(String awardId, int page, int size) {
        if (!awards.existsByAwardIdAndDeletedAtIsNull(awardId)) {
            return Optional.empty();
        }
        return Optional.of(subawards.findByPrimeAward(awardId, PageRequest.of(page - 1, size)));
    }

    /** The category taxonomy in display order. Only a migration changes it. */
    @Transactional(readOnly = true)
    public List<Category> categories() {
        return jdbc.sql("SELECT code, label, definition FROM taxonomy_category ORDER BY sort_order")
                .query(Category.class)
                .list();
    }

    /** Counts every live award, so it scans the table; callers cache it. */
    @Transactional(readOnly = true)
    public LiveAwards liveAwards() {
        return awards.liveAwards();
    }
}
