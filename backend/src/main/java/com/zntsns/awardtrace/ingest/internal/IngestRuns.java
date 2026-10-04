package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.internal.ArchiveListing.ArchiveFile;
import com.zntsns.awardtrace.ingest.internal.ArchiveListing.Kind;
import com.zntsns.awardtrace.ingest.internal.StoredFilePublisher.Publication;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Runs an ingest and records it in {@code ingest_run}. Backfill and delta store new archive files and freshly
 * generated subaward files (ADR 0014), then publish every stored file not yet published; replay publishes every
 * stored file again, from S3 alone. Contract files are published in file-date order, so a later file's deletes land
 * after the rows they delete (ADR 0012).
 */
@Component
@Profile("ingest")
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
    private final SubawardDownloads subawards;
    private final SourceFileStore store;
    private final StoredFilePublisher publisher;
    private final IngestProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;

    IngestRuns(ArchiveListing archive, SubawardDownloads subawards, SourceFileStore store,
            StoredFilePublisher publisher, IngestProperties properties, JdbcClient jdbc, Clock clock) {
        this.archive = archive;
        this.subawards = subawards;
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
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
                storeSubawardFiles(mode, runId);
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
        var files = new ArrayList<ArchiveFile>();
        for (int fiscalYear : fiscalYearsFrom(firstFiscalYear())) {
            files.addAll(archive.contractFiles("FY" + fiscalYear + "_", Kind.Full, properties.agencies()));
        }
        return files;
    }

    /**
     * Has USAspending generate each agency's subaward file for each fiscal year, and stores it. A delta takes the
     * previous fiscal year as well as the current one, because subawards are reported months after their actions.
     */
    private void storeSubawardFiles(Mode mode, UUID runId) throws IOException, InterruptedException {
        Map<String, String> names = subawards.agencyNames();
        Collection<String> agencies = properties.agencies().isEmpty() ? names.keySet() : properties.agencies();
        int from = mode == Mode.DELTA ? Math.max(firstFiscalYear(), currentFiscalYear() - 1) : firstFiscalYear();
        for (int fiscalYear : fiscalYearsFrom(from)) {
            for (String agency : agencies) {
                String name = names.get(agency);
                if (name == null) {
                    log.warn("USAspending lists no toptier agency {}, so its subawards are skipped", agency);
                    continue;
                }
                URI file = subawards.generate(name, LocalDate.of(fiscalYear - 1, 10, 1),
                        LocalDate.of(fiscalYear, 9, 30));
                store.store(file, "subawards/" + fiscalYear, runId);
            }
        }
    }

    /** FY2025, the start of scope. A fiscal year begins on October 1 of the year before its number. */
    private static int firstFiscalYear() {
        return ContractFileParser.SCOPE_START.plusMonths(3).getYear();
    }

    private int currentFiscalYear() {
        return LocalDate.now(clock).plusMonths(3).getYear();
    }

    private List<Integer> fiscalYearsFrom(int first) {
        return IntStream.rangeClosed(first, currentFiscalYear()).boxed().toList();
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
