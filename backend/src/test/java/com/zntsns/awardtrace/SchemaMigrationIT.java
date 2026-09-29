package com.zntsns.awardtrace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaMigrationIT {

    private static final String UEI = "ABCDEFGHJK12";

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void insertAgencyAndRecipient() {
        jdbc.sql("INSERT INTO agency (code, level, name) VALUES ('012', 'toptier', 'Department of Agriculture')")
                .update();
        jdbc.sql("INSERT INTO recipient (uei, name) VALUES (:uei, 'EXAMPLE LLC')").param("uei", UEI).update();
    }

    @Test
    void createsCoreTables() {
        var tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """)
                .query(String.class)
                .list();

        assertThat(tables).containsExactlyInAnyOrder(
                "agency", "recipient", "award", "award_transaction", "outbox", "ingest_run", "ingest_file");
    }

    @Test
    void derivesFiscalYearFromLastActionDate() {
        assertThat(fiscalYearOf(LocalDate.of(2025, 9, 30))).isEqualTo(2025);
        assertThat(fiscalYearOf(LocalDate.of(2025, 10, 1))).isEqualTo(2026);
        assertThat(fiscalYearOf(LocalDate.of(2026, 9, 30))).isEqualTo(2026);
    }

    @Test
    void checksTheAwardOfATransactionAtCommitNotAtInsert() {
        insertTransaction("TXN_1", "AWARD_1");
        insertAward("AWARD_1", LocalDate.of(2026, 3, 2));
        jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update();

        insertTransaction("TXN_2", "AWARD_WITHOUT_ROW");
        assertThatThrownBy(() -> jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int fiscalYearOf(LocalDate lastActionDate) {
        String awardId = "AWARD_" + lastActionDate;
        insertAward(awardId, lastActionDate);
        return jdbc.sql("SELECT fiscal_year FROM award WHERE award_id = :awardId")
                .param("awardId", awardId)
                .query(Integer.class)
                .single();
    }

    private void insertAward(String awardId, LocalDate lastActionDate) {
        jdbc.sql("""
                INSERT INTO award (award_id, piid, award_type, awarding_toptier_code, recipient_uei, total_obligated,
                                   first_action_date, last_action_date, source_modified_at, index_version,
                                   transaction_count)
                VALUES (:awardId, 'PIID', 'D', '012', :uei, 0, :date, :date, now(), 1, 1)
                """)
                .param("awardId", awardId)
                .param("uei", UEI)
                .param("date", lastActionDate)
                .update();
    }

    private void insertTransaction(String transactionId, String awardId) {
        jdbc.sql("""
                INSERT INTO award_transaction (transaction_id, award_id, modification_number, action_date,
                                               federal_action_obligation, source_modified_at, source_file_date,
                                               source_file, piid, award_type, awarding_toptier_code, total_obligated,
                                               recipient_uei, recipient_name)
                VALUES (:transactionId, :awardId, '0', DATE '2026-03-02', 0, now(), DATE '2026-09-09',
                        'FY2026_012_Contracts_Full_20260909_1.csv', 'PIID', 'D', '012', 0, :uei, 'EXAMPLE LLC')
                """)
                .param("transactionId", transactionId)
                .param("awardId", awardId)
                .param("uei", UEI)
                .update();
    }
}
