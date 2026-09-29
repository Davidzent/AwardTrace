package com.zntsns.awardtrace.ingest;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Ingest runs for other modules. */
@Service
public class IngestHistory {

    /** @param mode {@code backfill}, {@code delta}, or {@code replay}; status is running, succeeded, or failed */
    public record Run(UUID runId, String mode, String status, Instant startedAt, Instant finishedAt,
            long recordsPublished) {
    }

    /** @param lastRun the newest run, whatever its status; null before the first */
    public record IngestStatus(Run lastRun, Instant lastSuccessAt) {
    }

    private final JdbcClient jdbc;

    IngestHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public IngestStatus status() {
        var lastRun = jdbc.sql("""
                SELECT run_id, mode, status, started_at, finished_at, records_published
                FROM ingest_run ORDER BY started_at DESC LIMIT 1
                """)
                .query(Run.class)
                .optional()
                .orElse(null);
        // max() over no rows is a null value, which single() rejects.
        var lastSuccessAt = jdbc.sql("SELECT max(finished_at) FROM ingest_run WHERE status = 'succeeded'")
                .query(Instant.class)
                .optional()
                .orElse(null);
        return new IngestStatus(lastRun, lastSuccessAt);
    }
}
