package com.zntsns.awardtrace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaMigrationIT {

    @Autowired
    JdbcClient jdbc;

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

    private int fiscalYearOf(LocalDate lastActionDate) {
        jdbc.sql("INSERT INTO agency (code, level, name) VALUES ('097', 'toptier', 'Department of Defense') ON CONFLICT DO NOTHING")
                .update();
        jdbc.sql("""
                INSERT INTO recipient (uei, name, source_modified_at, first_seen_at, last_seen_at)
                VALUES ('ABCDEFGHJK12', 'EXAMPLE LLC', now(), now(), now())
                ON CONFLICT DO NOTHING
                """)
                .update();
        String awardId = "AWARD_" + lastActionDate;
        jdbc.sql("""
                INSERT INTO award (award_id, piid, award_type, awarding_toptier_code, recipient_uei, total_obligated,
                                   first_action_date, last_action_date, source_modified_at, version_transaction_id,
                                   index_version, transaction_count)
                VALUES (:awardId, 'PIID', 'D', '097', 'ABCDEFGHJK12', 0, :date, :date, now(), 'TXN', 1, 1)
                """)
                .param("awardId", awardId)
                .param("date", lastActionDate)
                .update();
        return jdbc.sql("SELECT fiscal_year FROM award WHERE award_id = :awardId")
                .param("awardId", awardId)
                .query(Integer.class)
                .single();
    }
}
