package com.zntsns.awardtrace.pipeline.internal;

import static com.zntsns.awardtrace.AwardRows.saveAward;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.subaward;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.pipeline.internal.SubawardWriter.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SubawardWriterIT {

    private static final String PRIME = "CONT_AWD_SUBAWARD_PRIME";

    @Autowired
    SubawardWriter writer;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        jdbc.sql("TRUNCATE subaward").update();
        writer.refreshNetwork();
        AwardRows.emptyTables(jdbc);
    }

    @Test
    void keepsTheNewestRevisionOfEachSubaward() {
        var first = subaward("S1", PRIME, "E2QCEKQXLN48", "DVORAK, LLC", "1000.00", "2026-08-01");
        var revised = subaward("S1", PRIME, "E2QCEKQXLN48", "DVORAK, LLC", "1500.00", "2026-08-15");

        assertThat(writer.write(List.of(first)).applied()).isEqualTo(1);
        assertThat(writer.write(List.of(revised)).applied()).isEqualTo(1);
        // A repeat, and an older revision arriving late, change nothing.
        assertThat(writer.write(List.of(revised, first)).applied()).isZero();

        assertThat(jdbc.sql("SELECT amount FROM subaward WHERE subaward_key = 'S1'").query(BigDecimal.class).single())
                .isEqualByComparingTo("1500.00");
    }

    @Test
    void announcesChangesToLivePrimeAwardsOnly() {
        saveAward(jdbc, PRIME, 3, "55000.00", false);
        saveAward(jdbc, "CONT_AWD_SUBAWARD_DELETED", 5, "0.00", true);

        Result result = writer.write(List.of(
                subaward("S1", PRIME, "E2QCEKQXLN48", "DVORAK, LLC", "1000.00", "2026-08-01"),
                subaward("S2", PRIME, "E2QCEKQXLN48", "DVORAK, LLC", "500.00", "2026-08-02"),
                subaward("S3", "CONT_AWD_SUBAWARD_DELETED", "E2QCEKQXLN48", "DVORAK, LLC", "1.00", "2026-08-01"),
                subaward("S4", "CONT_IDV_OUT_OF_SCOPE", "E2QCEKQXLN48", "DVORAK, LLC", "1.00", "2026-08-01")));

        assertThat(result).isEqualTo(new Result(4, 1));
        assertThat(jdbc.sql("SELECT aggregate_id || ' ' || change_reason || ' ' || index_version FROM outbox")
                .query(String.class)
                .list())
                .containsExactly(PRIME + " SUBAWARD 4");
        // Nothing changed the second time, so nothing is announced.
        assertThat(writer.write(List.of(subaward("S1", PRIME, "E2QCEKQXLN48", "DVORAK, LLC", "1000.00",
                "2026-08-01")))).isEqualTo(new Result(0, 0));
    }

    @Test
    void rollsSubawardsUpIntoTheRecipientNetwork() {
        writer.write(List.of(
                subaward("S1", PRIME, "E2QCEKQXLN48", "DVORAK LLC", "1000.00", "2026-08-01"),
                subaward("S2", "CONT_IDV_OUT_OF_SCOPE", "E2QCEKQXLN48", "DVORAK, LLC", "-200.00", "2026-08-20"),
                subaward("S3", PRIME, null, "UNREGISTERED VENDOR", "50.00", "2026-08-05")));

        writer.refreshNetwork();

        // Both subawards count toward one pair, named as most recently reported; the one without a UEI doesn't.
        assertThat(jdbc.sql("""
                SELECT prime_uei || ' ' || sub_uei || ' ' || sub_name || ' ' || subaward_count || ' ' || total_amount
                       || ' ' || first_action_date || ' ' || last_action_date
                FROM recipient_edge
                """)
                .query(String.class)
                .list())
                .containsExactly("MN5KRX2W9R46 E2QCEKQXLN48 DVORAK, LLC 2 800.00 2026-08-01 2026-08-20");
    }
}
