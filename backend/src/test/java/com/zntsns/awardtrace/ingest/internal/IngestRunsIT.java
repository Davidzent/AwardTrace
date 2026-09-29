package com.zntsns.awardtrace.ingest.internal;

import static com.zntsns.awardtrace.ingest.internal.Fixtures.DELTA_FILE;
import static com.zntsns.awardtrace.ingest.internal.Fixtures.FULL_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.ingest.internal.IngestRuns.Mode;
import com.zntsns.awardtrace.ingest.internal.IngestRuns.Summary;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestRunsIT {

    private static final String AGRICULTURE_FULL = "FY2026_012_Contracts_Full_20260906.zip";
    private static final String DEFENSE_FULL = "FY2026_097_Contracts_Full_20260906.zip";
    private static final String AGRICULTURE_DELTA = "FY(All)_012_Contracts_Delta_20260906.zip";

    private static final FakeArchive ARCHIVE = new FakeArchive()
            .put(AGRICULTURE_FULL, Fixtures.zip(FULL_FILE, Fixtures.csv(FULL_FILE)))
            .put(DEFENSE_FULL, Fixtures.zip(FULL_FILE, Fixtures.csv(FULL_FILE)))
            .put(AGRICULTURE_DELTA, Fixtures.zip(DELTA_FILE, Fixtures.csv(DELTA_FILE)));

    @DynamicPropertySource
    static void archive(DynamicPropertyRegistry registry) {
        registry.add("awardtrace.ingest.archive-url", ARCHIVE::url);
        registry.add("awardtrace.ingest.agencies", () -> "012");
    }

    @Autowired
    IngestRuns runs;

    @Autowired
    JdbcClient jdbc;

    @Test
    void backfillStoresAndPublishesTheConfiguredAgenciesFullFiles() throws Exception {
        Summary summary = runs.run(Mode.BACKFILL);

        // The full fixture holds 5 award rows and 2 IDV rows.
        assertThat(summary).isEqualTo(new Summary(summary.runId(), 1, 5, 2, 0));
        assertThat(ARCHIVE.downloads(DEFENSE_FULL)).isZero();
        assertThat(lastRun()).isEqualTo("backfill succeeded files=1 published=5 rejected=0");
    }

    @Test
    void deltaPublishesOnlyNewFilesAndReplayPublishesEveryStoredFileAgain() throws Exception {
        // The delta fixture holds 1 in-scope row, 2 deletes, 2 actions before FY2025, and 1 IDV row.
        assertThat(runs.run(Mode.DELTA)).extracting(Summary::files, Summary::published).containsExactly(1, 3L);
        assertThat(runs.run(Mode.DELTA)).extracting(Summary::files, Summary::published).containsExactly(0, 0L);
        assertThat(runs.run(Mode.REPLAY)).extracting(Summary::files, Summary::published).containsExactly(1, 3L);
        assertThat(ARCHIVE.downloads(AGRICULTURE_DELTA)).isEqualTo(1);
    }

    @Test
    void recordsAFailedRun() {
        ARCHIVE.failListings(true);
        try {
            assertThatThrownBy(() -> runs.run(Mode.DELTA)).isInstanceOf(IOException.class).hasMessageContaining("500");
        } finally {
            ARCHIVE.failListings(false);
        }

        assertThat(lastRun()).isEqualTo("delta failed files=0 published=0 rejected=0");
    }

    private String lastRun() {
        return jdbc.sql("""
                SELECT mode || ' ' || status || ' files=' || files_total || ' published=' || records_published
                       || ' rejected=' || records_rejected
                FROM ingest_run ORDER BY run_id DESC LIMIT 1
                """)
                .query(String.class)
                .single();
    }
}
