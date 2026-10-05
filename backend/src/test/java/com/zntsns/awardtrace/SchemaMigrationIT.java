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
                "agency", "recipient", "award", "award_transaction", "outbox", "ingest_run", "ingest_file",
                "subaward", "taxonomy_category", "psc_baseline_map", "classification", "enrichment_spend");
    }

    @Test
    void acceptsAReasonCodeOnlyOnAnUnclassifiableDescription() {
        insertClassification("1", "NATURAL_RESOURCES", null);
        insertClassification("2", "UNCLASSIFIABLE", "VAGUE");

        assertThatThrownBy(() -> insertClassification("3", "NATURAL_RESOURCES", "FAILED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void requiresAReasonCodeOnAnUnclassifiableDescription() {
        assertThatThrownBy(() -> insertClassification("4", "UNCLASSIFIABLE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsTheFourteenCategoriesInDisplayOrder() {
        assertThat(jdbc.sql("SELECT code FROM taxonomy_category ORDER BY sort_order").query(String.class).list())
                .containsExactly("IT_SOFTWARE", "IT_INFRASTRUCTURE", "CYBERSECURITY", "PROFESSIONAL_SERVICES",
                        "ENGINEERING_RESEARCH", "CONSTRUCTION_FACILITIES", "HEALTH_MEDICAL", "DEFENSE_SYSTEMS",
                        "LOGISTICS_TRANSPORT", "SUPPLIES_EQUIPMENT", "TRAINING_EDUCATION", "NATURAL_RESOURCES",
                        "OTHER", "UNCLASSIFIABLE");
    }

    @Test
    void mapsEveryPscGroupToABaselineCategory() {
        // Services start with a letter (PSC has no I or O); products start with an FSC group's first digit.
        for (char group : "123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray()) {
            assertThat(baselineCategoryOf(group + "999")).as("PSC group " + group).isNotNull();
        }
    }

    @Test
    void picksTheLongestMatchingPrefix() {
        assertThat(baselineCategoryOf("D399")).isEqualTo("IT_INFRASTRUCTURE");
        assertThat(baselineCategoryOf("DA01")).isEqualTo("IT_SOFTWARE");
        assertThat(baselineCategoryOf("DJ01")).isEqualTo("CYBERSECURITY");
        assertThat(baselineCategoryOf("D310")).isEqualTo("CYBERSECURITY");
        assertThat(baselineCategoryOf("7J20")).isEqualTo("CYBERSECURITY");
        assertThat(baselineCategoryOf("2310")).isEqualTo("LOGISTICS_TRANSPORT");
        assertThat(baselineCategoryOf("2350")).isEqualTo("DEFENSE_SYSTEMS");
        assertThat(baselineCategoryOf("R425")).isEqualTo("ENGINEERING_RESEARCH");
        assertThat(baselineCategoryOf("R408")).isEqualTo("PROFESSIONAL_SERVICES");
        assertThat(baselineCategoryOf("F003")).isEqualTo("NATURAL_RESOURCES");
        assertThat(baselineCategoryOf("G004")).isEqualTo("OTHER");
        assertThat(baselineCategoryOf(null)).isNull();
    }

    @Test
    void derivesFiscalYearFromLastActionDate() {
        assertThat(fiscalYearOf(LocalDate.of(2025, 9, 30))).isEqualTo(2025);
        assertThat(fiscalYearOf(LocalDate.of(2025, 10, 1))).isEqualTo(2026);
        assertThat(fiscalYearOf(LocalDate.of(2026, 9, 30))).isEqualTo(2026);
    }

    // SET CONSTRAINTS ALL IMMEDIATE runs the deferred checks now, as a commit would.
    @Test
    void acceptsATransactionWrittenBeforeItsAward() {
        insertTransaction("TXN_1", "AWARD_1");
        insertAward("AWARD_1", LocalDate.of(2026, 3, 2));

        jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update();
    }

    @Test
    void rejectsATransactionWhoseAwardIsNeverWritten() {
        insertTransaction("TXN_1", "AWARD_WITHOUT_ROW");

        assertThatThrownBy(() -> jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String baselineCategoryOf(String psc) {
        // single() rejects a null value, which a PSC without a category returns.
        return jdbc.sql("SELECT psc_baseline_category(CAST(:psc AS text))")
                .param("psc", psc)
                .query(String.class)
                .optional()
                .orElse(null);
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

    /** A classification whose description hash repeats {@code hashDigit} 64 times. */
    private void insertClassification(String hashDigit, String category, String reasonCode) {
        jdbc.sql("""
                INSERT INTO classification (description_hash, category, reason_code, model, prompt_version)
                VALUES (repeat(:digit, 64), :category, CAST(:reasonCode AS text), 'claude-haiku-4-5', 'v1')
                """)
                .param("digit", hashDigit)
                .param("category", category)
                .param("reasonCode", reasonCode)
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
