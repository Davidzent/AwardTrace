package com.zntsns.awardtrace.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IngestHistoryIT {

    @Autowired
    IngestHistory history;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyTable() {
        jdbc.sql("TRUNCATE ingest_run CASCADE").update();
    }

    @Test
    void reportsNothingBeforeTheFirstRun() {
        var status = history.status();

        assertThat(status.lastRun()).isNull();
        assertThat(status.lastSuccessAt()).isNull();
    }

    @Test
    void reportsNoSuccessWhileEveryRunHasFailed() {
        jdbc.sql("INSERT INTO ingest_run (mode, status, finished_at) VALUES ('backfill', 'failed', now())").update();

        var status = history.status();

        assertThat(status.lastRun().status()).isEqualTo("failed");
        assertThat(status.lastSuccessAt()).isNull();
    }
}
