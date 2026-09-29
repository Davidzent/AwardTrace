package com.zntsns.awardtrace.award;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static com.zntsns.awardtrace.AwardRows.saveTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.award.internal.AwardRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AwardQueriesIT {

    private static final String AWARD_ID = "CONT_AWD_QUERIES";

    @Autowired
    AwardQueries queries;

    @MockitoSpyBean
    AwardRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void readsTheAwardAndItsTransactionsFromOneSnapshot() {
        saveAward(jdbc, AWARD_ID, 3, "55000.00", false);
        saveTransaction(jdbc, AWARD_ID, "0", "2026-07-16", "27500.00", false);
        var pipeline = new TransactionTemplate(transactionManager);
        pipeline.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(invocation -> {
            // The repository is an interface proxy, so the spy's default answer, not callRealMethod, reaches it.
            Object award = mockingDetails(repository).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            // The pipeline commits a new modification between the two reads.
            pipeline.executeWithoutResult(status ->
                    saveTransaction(jdbc, AWARD_ID, "P00001", "2026-08-24", "27500.00", false));
            return award;
        }).when(repository).findLive(AWARD_ID);

        var found = queries.find(AWARD_ID, 10).orElseThrow();

        assertThat(found.transactions()).extracting(AwardTransaction::modificationNumber).containsExactly("0");
    }

    @Test
    void flagsTransactionsLeftOutByTheLimit() {
        saveAward(jdbc, AWARD_ID, 3, "55000.00", false);
        saveTransaction(jdbc, AWARD_ID, "0", "2026-07-16", "27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00001", "2026-08-24", "27500.00", false);
        saveTransaction(jdbc, AWARD_ID, "P00002", "2026-09-01", "100.00", false);

        var found = queries.find(AWARD_ID, 2).orElseThrow();

        assertThat(found.transactions()).extracting(AwardTransaction::modificationNumber)
                .containsExactly("P00002", "P00001");
        assertThat(found.truncated()).isTrue();
        assertThat(queries.find(AWARD_ID, 3).orElseThrow().truncated()).isFalse();
    }

    @Test
    void findsNothingForAMissingAward() {
        assertThat(queries.find("CONT_AWD_NONE", 10)).isEmpty();
    }
}
