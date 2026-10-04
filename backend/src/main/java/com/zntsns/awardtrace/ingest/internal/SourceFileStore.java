package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.shared.S3Config.S3Properties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Downloads USAspending source files into the raw bucket, which is the source of truth (ADR 0002). Each file is
 * fetched once: a known source URL is never downloaded again, and identical content is stored once, under a key
 * derived from its SHA-256.
 */
@Component
@Profile("ingest")
class SourceFileStore {

    static final String USER_AGENT = "AwardTrace/0.1 (+https://github.com/Davidzent/awardtrace)";

    /** Object metadata holding the file's source URL, which orders contract files when a replay has only S3. */
    static final String SOURCE_URL = "source-url";

    record StoredFile(String s3Key, String sourceUrl) {
    }

    // "FY2026_012_Contracts_Full_20260906.zip" or "FY(All)_012_Contracts_Delta_20260906.zip".
    private static final Pattern FISCAL_YEAR = Pattern.compile("^FY(\\d{4}|\\(All\\))_");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final S3Client s3;
    private final String bucket;
    private final JdbcClient jdbc;

    SourceFileStore(S3Client s3, S3Properties s3Properties, JdbcClient jdbc) {
        this.s3 = s3;
        this.bucket = s3Properties.bucket();
        this.jdbc = jdbc;
    }

    /** Stores an archive contract file under {@code raw/contracts/{fiscal year}/}, the year taken from its name. */
    String store(URI sourceUrl, UUID runId) throws IOException, InterruptedException {
        return store(sourceUrl, "contracts/" + fiscalYearOf(sourceUrl), runId);
    }

    /**
     * Returns the S3 key of the file at {@code sourceUrl}, downloading and storing it under {@code raw/{folder}/}
     * first if it is new.
     */
    String store(URI sourceUrl, String folder, UUID runId) throws IOException, InterruptedException {
        var known = jdbc.sql("SELECT s3_key FROM ingest_file WHERE source_url = :url")
                .param("url", sourceUrl.toString())
                .query(String.class)
                .optional();
        if (known.isPresent()) {
            return known.get();
        }

        Path download = Files.createTempFile("awardtrace-", ".zip");
        try {
            String sha256 = download(sourceUrl, download);
            String key = "raw/" + folder + "/" + sha256 + ".zip";
            long bytes = Files.size(download);
            // Upload before recording: a failure in between leaves an orphaned object, never a row without one.
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .metadata(Map.of(SOURCE_URL, sourceUrl.toString()))
                    .build(), RequestBody.fromFile(download));
            record(key, runId, sourceUrl.toString(), bytes);
            return key;
        } finally {
            Files.deleteIfExists(download);
        }
    }

    /**
     * Every file under {@code raw/}, listed from S3 rather than {@code ingest_file}, so a replay rebuilds from S3 alone
     * (ADR 0002). A file stored before objects carried their source URL takes it from its {@code ingest_file} row. A
     * file the database has lost is recorded again under {@code runId}.
     */
    List<StoredFile> storedFiles(UUID runId) {
        var files = new ArrayList<StoredFile>();
        for (S3Object object : s3.listObjectsV2Paginator(request -> request.bucket(bucket).prefix("raw/")).contents()) {
            String key = object.key();
            String sourceUrl = s3.headObject(request -> request.bucket(bucket).key(key)).metadata().get(SOURCE_URL);
            if (sourceUrl == null) {
                sourceUrl = jdbc.sql("SELECT source_url FROM ingest_file WHERE s3_key = :key")
                        .param("key", key)
                        .query(String.class)
                        .optional()
                        .orElseThrow(() -> new IllegalStateException("No source URL for " + key + " anywhere"));
            }
            record(key, runId, sourceUrl, object.size());
            files.add(new StoredFile(key, sourceUrl));
        }
        return files;
    }

    /** The key ends in the file's SHA-256, so a file already recorded, under any run, is left as it is. */
    private void record(String key, UUID runId, String sourceUrl, long bytes) {
        String sha256 = key.substring(key.lastIndexOf('/') + 1).replace(".zip", "");
        jdbc.sql("""
                INSERT INTO ingest_file (s3_key, run_id, source_url, sha256, bytes, status)
                VALUES (:key, :runId, :url, :sha256, :bytes, 'stored')
                ON CONFLICT DO NOTHING
                """)
                .param("key", key)
                .param("runId", runId)
                .param("url", sourceUrl)
                .param("sha256", sha256)
                .param("bytes", bytes)
                .update();
    }

    // ponytail: no retry on 429 or 5xx; the next monthly run retries a failed download.
    private String download(URI sourceUrl, Path target) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(sourceUrl).header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("GET " + sourceUrl + " returned " + response.statusCode());
        }
        MessageDigest digest = sha256();
        try (var body = new DigestInputStream(response.body(), digest)) {
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fiscalYearOf(URI sourceUrl) {
        String fileName = Path.of(sourceUrl.getPath()).getFileName().toString();
        var matcher = FISCAL_YEAR.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No fiscal year in source file name " + fileName);
        }
        return matcher.group(1).replace("(", "").replace(")", "").toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", e);
        }
    }
}
