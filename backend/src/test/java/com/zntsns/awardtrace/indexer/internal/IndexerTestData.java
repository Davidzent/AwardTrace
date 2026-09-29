package com.zntsns.awardtrace.indexer.internal;

import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Award rows as the pipeline would have written them, for tests that only need PostgreSQL to hold awards. */
final class IndexerTestData {

    private IndexerTestData() {
    }

    static void saveAward(JdbcClient jdbc, String awardId, long indexVersion, String totalObligated, boolean deleted) {
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

    static void emptyTables(JdbcClient jdbc) {
        jdbc.sql("TRUNCATE award_transaction, award, recipient, agency, outbox").update();
    }
}
