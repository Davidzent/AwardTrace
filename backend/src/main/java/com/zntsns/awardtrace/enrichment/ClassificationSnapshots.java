package com.zntsns.awardtrace.enrichment;

import com.zntsns.awardtrace.shared.S3Config.S3Properties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.SequenceWriter;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/**
 * Snapshots of the {@code classification} table in the raw bucket (doc 09). Classifications cost money to make, so
 * unlike the awards they can't be rebuilt from the source files for free (ADR 0002). Each enrichment run writes a
 * snapshot, and a replay restores the newest one before it publishes any file, so no description goes to Claude
 * twice.
 */
@Service
@Profile({"ingest", "enricher"})
public class ClassificationSnapshots {

    /** Under {@code raw/}, where nothing expires, in a folder replay doesn't publish as source files. */
    public static final String FOLDER = "raw/classifications/";

    /** @param key the snapshot's S3 key */
    public record Snapshot(String key, long classifications) {
    }

    private static final Logger log = LoggerFactory.getLogger(ClassificationSnapshots.class);

    private static final int PAGE = 10_000;
    private static final int BATCH = 1_000;
    private static final CsvMapper CSV = new CsvMapper();
    private static final CsvSchema COLUMNS = CsvSchema.builder()
            .addColumn("description_hash")
            .addColumn("category")
            .addColumn("confidence")
            .addColumn("reason_code")
            .addColumn("model")
            .addColumn("prompt_version")
            .addColumn("classified_at")
            .build()
            .withHeader();
    /** A key starts with when it was written, so keys sort by age, and ends with its content's SHA-256. */
    private static final DateTimeFormatter WRITTEN_AT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
    /** Object metadata holding how many classifications a snapshot has. */
    private static final String COUNT = "classifications";

    /** Every value as text, in one fixed form, so the same rows always make the same bytes. */
    private static final String PAGE_AFTER = """
            SELECT description_hash, category, confidence::text AS confidence, reason_code, model, prompt_version,
                   to_char(classified_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS classified_at
            FROM classification
            WHERE description_hash > :after
            ORDER BY description_hash
            LIMIT :limit
            """;

    /** A classification the table already holds stays as it is. */
    private static final String RESTORE = """
            INSERT INTO classification (description_hash, category, confidence, reason_code, model, prompt_version,
                                        classified_at)
            VALUES (:description_hash, :category, CAST(NULLIF(:confidence, '') AS numeric), NULLIF(:reason_code, ''),
                    :model, :prompt_version, CAST(:classified_at AS timestamptz))
            ON CONFLICT (description_hash) DO NOTHING
            """;

    private final S3Client s3;
    private final String bucket;
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batches;
    private final Clock clock;

    ClassificationSnapshots(S3Client s3, S3Properties s3Properties, JdbcClient jdbc, NamedParameterJdbcTemplate batches,
            Clock clock) {
        this.s3 = s3;
        this.bucket = s3Properties.bucket();
        this.jdbc = jdbc;
        this.batches = batches;
        this.clock = clock;
    }

    /**
     * Writes every classification to a gzipped CSV, a page at a time, so the table's size never sets the memory it
     * takes. If the newest snapshot already holds exactly these rows, nothing new is uploaded. Classifications are
     * never deleted, so a table with fewer than the newest snapshot has lost some, as a wiped database has: writing
     * it would hide the full snapshot from {@link #restore}, so it fails instead.
     */
    public Snapshot write() throws IOException {
        Path file = Files.createTempFile("awardtrace-classifications-", ".csv.gz");
        try {
            MessageDigest digest = sha256();
            long classifications = 0;
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(file), digest);
                    Writer text = new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8);
                    SequenceWriter csv = CSV.writer(COLUMNS).writeValues(text)) {
                String after = "";
                List<Map<String, Object>> page;
                do {
                    page = jdbc.sql(PAGE_AFTER).param("after", after).param("limit", PAGE).query().listOfRows();
                    for (Map<String, Object> row : page) {
                        csv.write(row);
                    }
                    classifications += page.size();
                    after = page.isEmpty() ? after : (String) page.getLast().get("description_hash");
                } while (page.size() == PAGE);
            }
            String sha = HexFormat.of().formatHex(digest.digest());
            Optional<String> newest = newestKey();
            if (newest.isPresent()) {
                if (newest.get().endsWith("-" + sha + ".csv.gz")) {
                    return new Snapshot(newest.get(), classifications);
                }
                long held = Long.parseLong(s3.headObject(request -> request.bucket(bucket).key(newest.get()))
                        .metadata().getOrDefault(COUNT, "0"));
                if (classifications < held) {
                    throw new IllegalStateException("The table has %d classifications, fewer than the %d in %s; "
                            .formatted(classifications, held, newest.get()) + "restore before writing a snapshot");
                }
            }
            String key = FOLDER + WRITTEN_AT.format(clock.instant()) + "-" + sha + ".csv.gz";
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .metadata(Map.of(COUNT, Long.toString(classifications)))
                    .build(), RequestBody.fromFile(file));
            log.info("Wrote {} classifications to {}", classifications, key);
            return new Snapshot(key, classifications);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * Adds the newest snapshot's classifications that the table doesn't hold, and returns how many it added: none
     * when there's no snapshot yet. It writes no outbox events; a replay restores before it rebuilds any award.
     */
    public long restore() throws IOException {
        Optional<String> key = newestKey();
        if (key.isEmpty()) {
            return 0;
        }
        long added = 0;
        try (InputStream in = s3.getObject(request -> request.bucket(bucket).key(key.get()));
                Reader text = new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8);
                MappingIterator<Map<String, String>> rows = CSV.readerForMapOf(String.class)
                        .with(CsvSchema.emptySchema().withHeader())
                        .readValues(text)) {
            var batch = new ArrayList<Map<String, String>>(BATCH);
            while (rows.hasNext()) {
                batch.add(rows.next());
                if (batch.size() == BATCH) {
                    added += insert(batch);
                    batch.clear();
                }
            }
            added += insert(batch);
        }
        log.info("Restored {} classifications from {}", added, key.get());
        return added;
    }

    private Optional<String> newestKey() {
        return s3.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(FOLDER)).contents().stream()
                .map(S3Object::key)
                .max(Comparator.naturalOrder());
    }

    private long insert(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        long added = 0;
        for (int count : batches.batchUpdate(RESTORE, SqlParameterSourceUtils.createBatch(rows))) {
            added += count;
        }
        return added;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
