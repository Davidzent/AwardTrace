package com.zntsns.awardtrace.award;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static com.zntsns.awardtrace.AwardRows.saveTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The context starting at all proves the read models match the Flyway schema, through ddl-auto=validate. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AwardRepositoryIT {

    private static final String AWARD_ID = "CONT_AWD_REPOSITORY";

    @Autowired
    AwardRepository awards;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void loadsALiveAwardWithItsRecipientAndAgencies() {
        saveAward(jdbc, AWARD_ID, 3, "55000.00", false);

        Award award = awards.findLive(AWARD_ID).orElseThrow();

        assertThat(award.totalObligated()).isEqualByComparingTo("55000.00");
        assertThat(award.fiscalYear()).isEqualTo(2026);
        assertThat(award.indexVersion()).isEqualTo(3);
        assertThat(award.recipient().name()).isEqualTo("NOMADIC LAND CAMPS, LLC");
        assertThat(award.awardingToptier().name()).isEqualTo("Department of Agriculture");
        assertThat(award.awardingSubtier().name()).isEqualTo("Forest Service");
        assertThat(award.fundingToptier()).isNull();
    }

    @Test
    void treatsADeletedAwardAsMissing() {
        saveAward(jdbc, AWARD_ID, 2, "0.00", true);

        assertThat(awards.findLive(AWARD_ID)).isEmpty();
    }

    @Test
    void listsLiveModificationsNewestFirstUpToTheLimit() {
        saveAward(jdbc, AWARD_ID, 3, "55000.00", false);
        saveTransaction(jdbc, AWARD_ID, "0", "2026-07-16", "27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00002", "2026-07-16", "-27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00001", "2026-08-24", "27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00003", "2026-09-01", "100.00", true);

        assertThat(awards.findLiveTransactions(AWARD_ID, Limit.of(10)))
                .extracting(AwardTransaction::modificationNumber, AwardTransaction::actionDate)
                .containsExactly(
                        tuple("P00001", LocalDate.of(2026, 8, 24)),
                        tuple("P00002", LocalDate.of(2026, 7, 16)),
                        tuple("0", LocalDate.of(2026, 7, 16)));
        assertThat(awards.findLiveTransactions(AWARD_ID, Limit.of(2))).hasSize(2);
    }
}
