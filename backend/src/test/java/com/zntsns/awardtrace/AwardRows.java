package com.zntsns.awardtrace;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Award rows as the pipeline would have written them, for tests that only need PostgreSQL to hold awards. */
public final class AwardRows {

    private AwardRows() {
    }

    public static void saveAward(JdbcClient jdbc, String awardId, long indexVersion, String totalObligated, boolean deleted) {
        jdbc.sql("""
                INSERT INTO agency (code, level, name) VALUES ('012', 'toptier', 'Department of Agriculture')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO agency (code, level, name, parent_code) VALUES ('12C2', 'subtier', 'Forest Service', '012')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO recipient (uei, name) VALUES ('MN5KRX2W9R46', 'NOMADIC LAND CAMPS, LLC')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO award (award_id, piid, award_type, description, awarding_toptier_code,
                                   awarding_subtier_code, recipient_uei, naics_code, psc_code, pop_state_code,
                                   total_obligated, first_action_date, last_action_date, source_modified_at,
                                   index_version, transaction_count, deleted_at)
                VALUES (:awardId, '12024B26M0522', 'C', 'NOMADIC LAND CAMPS, LLC IDIPF000347 E40', '012', '12C2',
                        'MN5KRX2W9R46', '517810', 'F003', 'ID', :total, DATE '2026-07-16', DATE '2026-08-24', now(),
                        :indexVersion, 3, CASE WHEN :deleted THEN now() END)
                ON CONFLICT (award_id) DO UPDATE SET total_obligated = EXCLUDED.total_obligated,
                    index_version = EXCLUDED.index_version, deleted_at = EXCLUDED.deleted_at
                """)
                .param("awardId", awardId)
                .param("total", new BigDecimal(totalObligated))
                .param("indexVersion", indexVersion)
                .param("deleted", deleted)
                .update();
    }

    /** A live award of the Department of Agriculture with the fields search ranks, filters, and sorts on. */
    public static void saveSearchableAward(JdbcClient jdbc, String awardId, String piid, String description,
            String recipientUei, String recipientName, String popStateCode, String totalObligated,
            String lastActionDate) {
        saveAward(jdbc, "CONT_AWD_SEED_AGENCIES", 1, "0.00", true);
        jdbc.sql("INSERT INTO recipient (uei, name) VALUES (:uei, :name) ON CONFLICT DO NOTHING")
                .param("uei", recipientUei)
                .param("name", recipientName)
                .update();
        jdbc.sql("""
                INSERT INTO award (award_id, piid, award_type, description, awarding_toptier_code,
                                   awarding_subtier_code, recipient_uei, naics_code, naics_description, psc_code,
                                   pop_state_code, total_obligated, first_action_date, last_action_date,
                                   source_modified_at, index_version, transaction_count)
                VALUES (:awardId, :piid, 'C', :description, '012', '12C2', :uei, '517810',
                        'ALL OTHER TELECOMMUNICATIONS', 'F003', :state, :total, CAST(:lastActionDate AS date),
                        CAST(:lastActionDate AS date), now(), 1, 1)
                """)
                .param("awardId", awardId)
                .param("piid", piid)
                .param("description", description)
                .param("uei", recipientUei)
                .param("state", popStateCode)
                .param("total", new BigDecimal(totalObligated))
                .param("lastActionDate", lastActionDate)
                .update();
    }

    /** A live or deleted modification of an award saved with {@link #saveAward}. */
    public static void saveTransaction(JdbcClient jdbc, String awardId, String modification, String actionDate,
            String obligation, boolean deleted) {
        jdbc.sql("""
                INSERT INTO award_transaction (transaction_id, award_id, modification_number, action_date,
                                               federal_action_obligation, description, source_modified_at,
                                               source_file_date, source_file, piid, award_type,
                                               awarding_toptier_code, total_obligated, recipient_uei,
                                               recipient_name, deleted_at)
                VALUES (:awardId || '_' || :modification, :awardId, :modification, CAST(:actionDate AS date),
                        :obligation, 'MOD ' || :modification, now(), DATE '2026-09-09',
                        'FY2026_012_Contracts_Full_20260909_1.csv', '12024B26M0522', 'C', '012', 0,
                        'MN5KRX2W9R46', 'NOMADIC LAND CAMPS, LLC', CASE WHEN :deleted THEN now() END)
                """)
                .param("awardId", awardId)
                .param("modification", modification)
                .param("actionDate", actionDate)
                .param("obligation", new BigDecimal(obligation))
                .param("deleted", deleted)
                .update();
    }

    /** A subaward reported by the recipient of {@link #saveAward}; a null {@code subUei} is an unregistered vendor. */
    public static void saveSubaward(JdbcClient jdbc, String key, String awardId, String subUei, String subName,
            String amount, String actionDate) {
        saveSubaward(jdbc, key, awardId, "MN5KRX2W9R46", "NOMADIC LAND CAMPS, LLC", subUei, subName, amount,
                actionDate);
    }

    /** A subaward reported by any prime. The recipient network sees it once {@code recipient_edge} is refreshed. */
    public static void saveSubaward(JdbcClient jdbc, String key, String awardId, String primeUei, String primeName,
            String subUei, String subName, String amount, String actionDate) {
        jdbc.sql("""
                INSERT INTO subaward (subaward_key, prime_award_id, prime_recipient_uei, prime_recipient_name,
                                      sub_recipient_uei, sub_recipient_name, amount, action_date, description,
                                      source_modified_at)
                VALUES (:key, :awardId, :primeUei, :primeName, :subUei, :subName, :amount, CAST(:actionDate AS date),
                        'Subaward ' || :key, now())
                """)
                .param("key", key)
                .param("awardId", awardId)
                .param("primeUei", primeUei)
                .param("primeName", primeName)
                .param("subUei", subUei)
                .param("subName", subName)
                .param("amount", new BigDecimal(amount))
                .param("actionDate", actionDate)
                .update();
    }

    /** Gives the awards one description hash, as the pipeline does for awards whose descriptions match. */
    public static void describe(JdbcClient jdbc, String descriptionHash, String... awardIds) {
        jdbc.sql("UPDATE award SET description_hash = :hash WHERE award_id IN (:awardIds)")
                .param("hash", descriptionHash)
                .param("awardIds", List.of(awardIds))
                .update();
    }

    public static void emptyTables(JdbcClient jdbc) {
        jdbc.sql("TRUNCATE award_transaction, award, recipient, agency, outbox, subaward, classification").update();
        jdbc.sql("REFRESH MATERIALIZED VIEW recipient_edge").update();
    }
}
