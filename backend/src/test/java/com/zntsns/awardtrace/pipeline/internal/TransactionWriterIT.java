package com.zntsns.awardtrace.pipeline.internal;

import static com.zntsns.awardtrace.pipeline.internal.TestEvents.AWARD_ID;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.deletion;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.file;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.pipeline.internal.TransactionWriter.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Each write commits, so the deferred award foreign key is checked as it is in production. The award is the one in
 * the full fixture: its base action and a same-day deobligation on 2026-07-16, and an option exercised 2026-08-24.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransactionWriterIT {

    private static final String SEPT_8 = file("20260908");
    private static final String SEPT_9 = file("20260909");
    private static final String OCT_7 = file("20261007");

    private final ContractTransactionIngested base = transaction("0", "2026-07-16", "27500.00", "27500.00", SEPT_9);
    private final ContractTransactionIngested deobligation =
            transaction("P00002", "2026-07-16", "-27500.00", "27500.00", SEPT_9);
    private final ContractTransactionIngested option =
            transaction("P00001", "2026-08-24", "27500.00", "55000.00", SEPT_9);

    record AwardRow(BigDecimal totalObligated, int transactionCount, LocalDate firstActionDate,
            LocalDate lastActionDate, int fiscalYear, long indexVersion, boolean deleted) {
    }

    @Autowired
    TransactionWriter writer;

    @Autowired
    JdbcClient jdbc;

    // Other integration tests share this database and roll back, so committed rows must not outlive a test.
    @AfterEach
    void emptyTables() {
        jdbc.sql("TRUNCATE award_transaction, award, recipient, agency, outbox").update();
    }

    @Test
    void projectsTheAwardFromItsLatestTransactionInActionOrder() {
        Result result = write(base, deobligation, option);

        assertThat(result).isEqualTo(new Result(3, 0, 0, 1));
        assertThat(award()).isEqualTo(new AwardRow(new BigDecimal("55000.00"), 3, LocalDate.of(2026, 7, 16),
                LocalDate.of(2026, 8, 24), 2026, 1, false));
        assertThat(count("recipient")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT code || ':' || level FROM agency ORDER BY code").query(String.class).list())
                .containsExactly("012:toptier", "12C2:subtier");
        assertThat(outboxVersions()).containsExactly(1L);
    }

    @Test
    void writesNothingForARedeliveredBatch() {
        write(base, deobligation, option);

        Result redelivery = write(base, deobligation, option);

        assertThat(redelivery).isEqualTo(new Result(0, 3, 0, 0));
        assertThat(award().indexVersion()).isEqualTo(1);
        assertThat(outboxVersions()).containsExactly(1L);
    }

    @Test
    void reachesTheSameAwardWhateverTheDeliveryOrder() {
        write(option);
        write(base);
        write(deobligation);

        assertThat(award()).isEqualTo(new AwardRow(new BigDecimal("55000.00"), 3, LocalDate.of(2026, 7, 16),
                LocalDate.of(2026, 8, 24), 2026, 3, false));
    }

    @Test
    void keepsTheVersionFromTheNewestSourceFile() {
        write(option);

        Result olderCopy = write(transaction("P00001", "2026-08-24", "27500.00", "99999.00", SEPT_8));

        assertThat(olderCopy.stale()).isEqualTo(1);
        assertThat(award().totalObligated()).isEqualByComparingTo("55000.00");
    }

    @Test
    void appliesTheNewestVersionWhenOneBatchHoldsSeveral() {
        write(transaction("P00001", "2026-08-24", "27500.00", "77000.00", OCT_7),
                option,
                transaction("P00001", "2026-08-24", "27500.00", "99999.00", SEPT_8));

        assertThat(award().totalObligated()).isEqualByComparingTo("77000.00");
    }

    @Test
    void handsTheAwardBackToThePreviousTransactionWhenTheLatestIsDeleted() {
        write(base, option);

        Result firstDelete = write(List.of(), List.of(deletion("P00001", OCT_7)));

        assertThat(firstDelete).isEqualTo(new Result(0, 0, 1, 1));
        assertThat(award()).isEqualTo(new AwardRow(new BigDecimal("27500.00"), 1, LocalDate.of(2026, 7, 16),
                LocalDate.of(2026, 7, 16), 2026, 2, false));

        write(List.of(), List.of(deletion("0", OCT_7)));

        assertThat(award().deleted()).isTrue();
        assertThat(outboxVersions()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void keepsADeletedTransactionDeletedWhenAnOlderCopyArrivesLater() {
        write(base);
        write(List.of(), List.of(deletion("0", OCT_7)));

        Result replayedOlderFile = write(base);

        assertThat(replayedOlderFile.stale()).isEqualTo(1);
        assertThat(award().deleted()).isTrue();
    }

    @Test
    void ignoresADeleteForATransactionNeverStored() {
        Result result = write(List.of(), List.of(deletion("P00009", OCT_7)));

        assertThat(result).isEqualTo(new Result(0, 0, 0, 0));
        assertThat(count("award")).isZero();
    }

    @Test
    void storesAnAwardAsDeletedWhenItsOnlyTransactionIsDeletedInTheSameBatch() {
        Result result = write(List.of(base), List.of(deletion("0", OCT_7)));

        assertThat(result).isEqualTo(new Result(1, 0, 1, 1));
        assertThat(award().deleted()).isTrue();
    }

    private Result write(ContractTransactionIngested... ingested) {
        return write(List.of(ingested), List.of());
    }

    private Result write(List<ContractTransactionIngested> ingested, List<ContractTransactionDeleted> deleted) {
        return writer.write(ingested, deleted);
    }

    private AwardRow award() {
        return jdbc.sql("""
                SELECT total_obligated, transaction_count, first_action_date, last_action_date, fiscal_year,
                       index_version, deleted_at IS NOT NULL AS deleted
                FROM award WHERE award_id = :awardId
                """)
                .param("awardId", AWARD_ID)
                .query(AwardRow.class)
                .single();
    }

    private List<Long> outboxVersions() {
        return jdbc.sql("SELECT index_version FROM outbox WHERE aggregate_id = :awardId ORDER BY id")
                .param("awardId", AWARD_ID)
                .query(Long.class)
                .list();
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
