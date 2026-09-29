package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SourceFileStoreIT {

    private static final String PATH = "/award_data_archive/FY2026_012_Contracts_Full_20260906.zip";

    @Autowired
    SourceFileStore store;

    @Autowired
    S3Client s3;

    @Autowired
    JdbcClient jdbc;

    private final byte[] zip = Fixtures.zip(Fixtures.FULL_FILE, "contract_transaction_unique_key\n");
    private final AtomicInteger downloads = new AtomicInteger();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private HttpServer archive;
    private UUID runId;

    @BeforeEach
    void startArchiveAndRun() throws IOException {
        archive = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        archive.createContext(PATH, exchange -> {
            downloads.incrementAndGet();
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.sendResponseHeaders(200, zip.length);
            exchange.getResponseBody().write(zip);
            exchange.close();
        });
        archive.start();
        runId = jdbc.sql("INSERT INTO ingest_run (mode, status) VALUES ('backfill', 'running') RETURNING run_id")
                .query(UUID.class)
                .single();
    }

    @AfterEach
    void stopArchive() {
        archive.stop(0);
    }

    @Test
    void storesTheFileUnderItsChecksumAndRecordsIt() throws Exception {
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip));

        String key = store.store(sourceUrl(), runId);

        assertThat(key).isEqualTo("raw/contracts/2026/" + sha256 + ".zip");
        assertThat(s3.getObjectAsBytes(request -> request.bucket(TestcontainersConfiguration.RAW_BUCKET).key(key))
                .asByteArray()).isEqualTo(zip);
        assertThat(jdbc.sql("SELECT sha256, bytes, status, run_id FROM ingest_file WHERE s3_key = :key")
                .param("key", key)
                .query(StoredFile.class)
                .single())
                .isEqualTo(new StoredFile(sha256, zip.length, "stored", runId));
        assertThat(userAgent).hasValue(SourceFileStore.USER_AGENT);
    }

    @Test
    void neverDownloadsAKnownSourceUrlAgain() throws Exception {
        String first = store.store(sourceUrl(), runId);
        String second = store.store(sourceUrl(), runId);

        assertThat(second).isEqualTo(first);
        assertThat(downloads).hasValue(1);
    }

    @Test
    void storesAGeneratedFileUnderTheFolderItIsGiven() throws Exception {
        String key = store.store(sourceUrl(), "subawards/2026", runId);

        assertThat(key).startsWith("raw/subawards/2026/").endsWith(".zip");
        assertThat(s3.getObjectAsBytes(request -> request.bucket(TestcontainersConfiguration.RAW_BUCKET).key(key))
                .asByteArray()).isEqualTo(zip);
    }

    record StoredFile(String sha256, long bytes, String status, UUID runId) {
    }

    private URI sourceUrl() {
        return URI.create("http://localhost:" + archive.getAddress().getPort() + PATH);
    }
}
