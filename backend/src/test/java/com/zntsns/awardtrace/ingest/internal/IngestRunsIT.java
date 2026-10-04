package com.zntsns.awardtrace.ingest.internal;

import static com.zntsns.awardtrace.ingest.internal.Fixtures.DELTA_FILE;
import static com.zntsns.awardtrace.ingest.internal.Fixtures.FULL_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.ingest.internal.IngestRuns.Mode;
import com.zntsns.awardtrace.ingest.internal.IngestRuns.Summary;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The clock stands at 2026-11-01, early in FY2027: a backfill covers FY2025 through FY2027, and a delta the current
 * and previous fiscal years. Each generated subaward file holds the fixture's 3 subawards, 1 action before scope,
 * and 1 unreadable row.
 */
@SpringBootTest
@ActiveProfiles("ingest")
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestRunsIT {

    private static final String AGRICULTURE_FULL = "FY2026_012_Contracts_Full_20260906.zip";
    private static final String DEFENSE_FULL = "FY2026_097_Contracts_Full_20260906.zip";
    private static final String AGRICULTURE_DELTA = "FY(All)_012_Contracts_Delta_20260906.zip";

    private static final FakeUsaspending USASPENDING = new FakeUsaspending()
            .put(AGRICULTURE_FULL, Fixtures.zip(FULL_FILE, Fixtures.csv(FULL_FILE)))
            .put(DEFENSE_FULL, Fixtures.zip(FULL_FILE, Fixtures.csv(FULL_FILE)))
            .put(AGRICULTURE_DELTA, Fixtures.zip(DELTA_FILE, Fixtures.csv(DELTA_FILE)));

    @DynamicPropertySource
    static void archive(DynamicPropertyRegistry registry) {
        registry.add("awardtrace.ingest.archive-url", USASPENDING::url);
        registry.add("awardtrace.ingest.api-url", USASPENDING::apiUrl);
        registry.add("awardtrace.ingest.download-poll-interval", () -> "10ms");
        registry.add("awardtrace.ingest.agencies", () -> "012");
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    IngestRuns runs;

    @Autowired
    S3Client s3;

    @Autowired
    MeterRegistry meters;

    @Autowired
    JdbcClient jdbc;

    /** The database rolls back after each test, but S3 keeps its files, and a replay publishes every file in S3. */
    @BeforeEach
    void emptyBucket() {
        s3.listObjectsV2Paginator(request -> request.bucket(TestcontainersConfiguration.RAW_BUCKET)).contents()
                .forEach(object -> s3.deleteObject(request -> request.bucket(TestcontainersConfiguration.RAW_BUCKET)
                        .key(object.key())));
    }

    @Test
    void backfillStoresTheConfiguredAgenciesFullFilesAndEveryYearsSubawards() throws Exception {
        int requestsBefore = USASPENDING.subawardRequests().size();
        double publishedBefore = records("published");
        double rejectedBefore = records("rejected");

        Summary summary = runs.run(Mode.BACKFILL);

        // The full contract fixture holds 5 award rows and 2 IDV rows.
        assertThat(summary).isEqualTo(new Summary(summary.runId(), 4, 5 + 3 * 3, 2 + 3, 3));
        assertThat(USASPENDING.downloads(DEFENSE_FULL)).isZero();
        assertThat(USASPENDING.subawardRequests().subList(requestsBefore, requestsBefore + 3)).containsExactly(
                "Department of Agriculture 2024-10-01 2025-09-30",
                "Department of Agriculture 2025-10-01 2026-09-30",
                "Department of Agriculture 2026-10-01 2027-09-30");
        assertThat(lastRun()).isEqualTo("backfill succeeded files=4 published=14 rejected=3");
        assertThat(records("published") - publishedBefore).isEqualTo(14);
        assertThat(records("rejected") - rejectedBefore).isEqualTo(3);
    }

    private double records(String result) {
        return meters.counter("awardtrace.ingest.records", "result", result).count();
    }

    @Test
    void deltaPublishesNewFilesAndRegeneratedSubawardsAndReplayPublishesEveryStoredFileAgain() throws Exception {
        // The contract delta fixture holds 1 in-scope row, 2 deletes, 2 actions before FY2025, and 1 IDV row. Each
        // delta regenerates FY2026 and FY2027 subaward files, whose bytes always differ, so they are always new.
        int downloadsBefore = USASPENDING.downloads(AGRICULTURE_DELTA);
        assertThat(runs.run(Mode.DELTA)).extracting(Summary::files, Summary::published).containsExactly(3, 3 + 6L);
        assertThat(runs.run(Mode.DELTA)).extracting(Summary::files, Summary::published).containsExactly(2, 6L);
        assertThat(runs.run(Mode.REPLAY)).extracting(Summary::files, Summary::published).containsExactly(5, 3 + 12L);
        assertThat(USASPENDING.downloads(AGRICULTURE_DELTA)).isEqualTo(downloadsBefore + 1);
    }

    @Test
    void recordsAFailedRun() {
        USASPENDING.failListings(true);
        try {
            assertThatThrownBy(() -> runs.run(Mode.DELTA)).isInstanceOf(IOException.class).hasMessageContaining("500");
        } finally {
            USASPENDING.failListings(false);
        }

        assertThat(lastRun()).isEqualTo("delta failed files=0 published=0 rejected=0");
    }

    @Test
    void failsTheRunWhenUsaspendingCantGenerateASubawardFile() {
        USASPENDING.failGeneration(true);
        try {
            assertThatThrownBy(() -> runs.run(Mode.DELTA))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("generation failed");
        } finally {
            USASPENDING.failGeneration(false);
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
