package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.internal.ArchiveListing.ArchiveFile;
import com.zntsns.awardtrace.ingest.internal.ArchiveListing.Kind;
import com.zntsns.awardtrace.ingest.internal.StoredFilePublisher.Publication;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Runs an ingest and records it in {@code ingest_run}. Backfill and delta store new archive files, then publish
 * every stored file not yet published; replay publishes every stored file again, from S3 alone. Files are published
 * in file-date order, so a later file's deletes land after the rows they delete (ADR 0012).
 */
@Component
@EnableConfigurationProperties(IngestProperties.class)
class IngestRuns {

    private static final Logger log = LoggerFactory.getLogger(IngestRuns.class);
    private static final Pattern URL_FILE_DATE = Pattern.compile("_(\\d{8})\\.zip$");

    enum Mode { BACKFILL, DELTA, REPLAY }

    record Summary(UUID runId, int files, long published, long skipped, long rejected) {
    }

    private record StoredFile(String s3Key, String sourceUrl) {
    }

    private final ArchiveListing archive;
    private final SourceFileStore store;
    private final StoredFilePublisher publisher;
    private final IngestProperties properties;
    private final JdbcClient jdbc;

    IngestRuns(ArchiveListing archive, SourceFileStore store, StoredFilePublisher publisher,
            IngestProperties properties, JdbcClient jdbc) {
        this.archive = archive;
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    Summary run(Mode mode) throws IOException, InterruptedException {
        UUID runId = jdbc.sql("INSERT INTO ingest_run (mode, status) VALUES (:mode, 'running') RETURNING run_id")
                .param("mode", mode.name().toLowerCase(Locale.ROOT))
                .query(UUID.class)
                .single();
        try {
            if (mode != Mode.REPLAY) {
                for (ArchiveFile file : archiveFiles(mode)) {
                    store.store(file.url(), runId);
                }
            }
            List<String> keys = filesToPublish(mode == Mode.REPLAY);
            long published = 0;
            long skipped = 0;
            long rejected = 0;
            for (String key : keys) {
                Publication publication = publisher.publish(key, runId);
                published += publication.published();
                skipped += publication.skipped();
                rejected += publication.rejected();
            }
            jdbc.sql("""
                    UPDATE ingest_run SET status = 'succeeded', finished_at = now(), files_total = :files,
                                          records_published = :published, records_rejected = :rejected
                    WHERE run_id = :runId
                    """)
                    .param("files", keys.size())
                    .param("published", published)
                    .param("rejected", rejected)
                    .param("runId", runId)
                    .update();
            var summary = new Summary(runId, keys.size(), published, skipped, rejected);
            log.info("Ingest {} finished: {}", mode, summary);
            return summary;
        } catch (IOException | InterruptedException | RuntimeException e) {
            jdbc.sql("UPDATE ingest_run SET status = 'failed', finished_at = now(), error = :error WHERE run_id = :runId")
                    .param("error", e.toString())
                    .param("runId", runId)
                    .update();
            throw e;
        }
    }

    private List<ArchiveFile> archiveFiles(Mode mode) throws IOException, InterruptedException {
        if (mode == Mode.DELTA) {
            return archive.contractFiles("FY(All)_", Kind.Delta, properties.agencies());
        }
        // Every fiscal year from FY2025, the start of scope, to the current one, which begins each October 1.
        var files = new ArrayList<ArchiveFile>();
        int currentFiscalYear = LocalDate.now(ZoneOffset.UTC).plusMonths(3).getYear();
        int firstFiscalYear = ContractFileParser.SCOPE_START.plusMonths(3).getYear();
        for (int fiscalYear = firstFiscalYear; fiscalYear <= currentFiscalYear; fiscalYear++) {
            files.addAll(archive.contractFiles("FY" + fiscalYear + "_", Kind.Full, properties.agencies()));
        }
        return files;
    }

    private List<String> filesToPublish(boolean all) {
        return jdbc.sql("SELECT s3_key, source_url FROM ingest_file" + (all ? "" : " WHERE status = 'stored'"))
                .query(StoredFile.class)
                .list()
                .stream()
                .sorted(Comparator.comparing((StoredFile file) -> fileDateOf(file.sourceUrl()))
                        .thenComparing(StoredFile::sourceUrl))
                .map(StoredFile::s3Key)
                .toList();
    }

    private static String fileDateOf(String sourceUrl) {
        var matcher = URL_FILE_DATE.matcher(sourceUrl);
        return matcher.find() ? matcher.group(1) : "";
    }
}
