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
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Downloads USAspending source files into the raw bucket, which is the source of truth (ADR 0002). Each file is
 * fetched once: a known source URL is never downloaded again, and identical content is stored once, under a key
 * derived from its SHA-256.
 */
@Component
class SourceFileStore {

    static final String USER_AGENT = "AwardTrace/0.1 (+https://github.com/Davidzent/awardtrace)";

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
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromFile(download));
            jdbc.sql("""
                    INSERT INTO ingest_file (s3_key, run_id, source_url, sha256, bytes, status)
                    VALUES (:key, :runId, :url, :sha256, :bytes, 'stored')
                    ON CONFLICT DO NOTHING
                    """)
                    .param("key", key)
                    .param("runId", runId)
                    .param("url", sourceUrl.toString())
                    .param("sha256", sha256)
                    .param("bytes", bytes)
                    .update();
            return key;
        } finally {
            Files.deleteIfExists(download);
        }
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
