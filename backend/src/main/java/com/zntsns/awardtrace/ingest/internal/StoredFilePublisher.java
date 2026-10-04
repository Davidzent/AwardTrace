package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.ingest.internal.ParsedRow.Deleted;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Ingested;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Rejected;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Reported;
import com.zntsns.awardtrace.ingest.internal.ParsedRow.Skipped;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.S3Config.S3Properties;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Publishes every in-scope row of a file stored in the raw bucket. Reads only from S3, so the same code serves a
 * first load and a replay (ADR 0002). A file is marked published only after the broker acknowledges every event;
 * publishing it again is harmless because the pipeline is idempotent.
 */
@Component
@Profile("ingest")
class StoredFilePublisher {

    private static final Logger log = LoggerFactory.getLogger(StoredFilePublisher.class);

    private final S3Client s3;
    private final String bucket;
    private final ContractFileParser contracts = new ContractFileParser();
    private final SubawardFileParser subawards = new SubawardFileParser();
    private final EventPublisher events;
    private final JdbcClient jdbc;

    StoredFilePublisher(S3Client s3, S3Properties s3Properties, EventPublisher events, JdbcClient jdbc) {
        this.s3 = s3;
        this.bucket = s3Properties.bucket();
        this.events = events;
        this.jdbc = jdbc;
    }

    record Publication(long rows, long published, long skipped, long rejected) {
    }

    Publication publish(String s3Key, UUID runId) throws IOException {
        var published = new AtomicLong();
        var skipped = new AtomicLong();
        var rejected = new AtomicLong();
        var sendFailure = new AtomicReference<Throwable>();

        // Source zips hold one CSV. A contract CSV's name carries the date that versions its rows; a subaward file,
        // stored under raw/subawards/, versions each row itself (ADR 0014).
        try (var zip = new ZipInputStream(s3.getObject(request -> request.bucket(bucket).key(s3Key)))) {
            var entry = zip.getNextEntry();
            if (entry == null || !entry.getName().endsWith(".csv")) {
                throw new IOException(s3Key + " holds no CSV file");
            }
            var csv = new InputStreamReader(zip, StandardCharsets.UTF_8);
            try (var rows = s3Key.startsWith("raw/subawards/")
                    ? subawards.parse(csv)
                    : contracts.parse(csv, entry.getName())) {
                rows.forEach(row -> {
                    var source = new EventEnvelope.Source(runId, s3Key, row.rowNumber());
                    switch (row) {
                        case Ingested ingested -> {
                            send(KafkaTopics.AWARD_TRANSACTIONS, ingested.event().awardId(), ingested.event(), source,
                                    sendFailure);
                            published.incrementAndGet();
                        }
                        case Deleted deleted -> {
                            send(KafkaTopics.AWARD_TRANSACTIONS, deleted.event().awardId(), deleted.event(), source,
                                    sendFailure);
                            published.incrementAndGet();
                        }
                        case Reported reported -> {
                            send(KafkaTopics.SUBAWARDS, reported.event().primeAwardId(), reported.event(), source,
                                    sendFailure);
                            published.incrementAndGet();
                        }
                        case Skipped ignored -> skipped.incrementAndGet();
                        case Rejected rejection -> {
                            rejected.incrementAndGet();
                            log.warn("Rejected row {} of {}: {}", rejection.rowNumber(), s3Key, rejection.reason());
                        }
                    }
                });
            }
        }
        events.flush();
        if (sendFailure.get() != null) {
            throw new IOException("Publishing " + s3Key + " failed; it stays unpublished", sendFailure.get());
        }

        long rows = published.get() + skipped.get() + rejected.get();
        jdbc.sql("""
                UPDATE ingest_file SET status = 'published', row_count = :rows, published_at = now()
                WHERE s3_key = :key
                """)
                .param("rows", rows)
                .param("key", s3Key)
                .update();
        return new Publication(rows, published.get(), skipped.get(), rejected.get());
    }

    private void send(String topic, String awardId, Object event, EventEnvelope.Source source,
            AtomicReference<Throwable> failure) {
        events.publish(topic, awardId, event, source).whenComplete((result, error) -> {
            if (error != null) {
                failure.compareAndSet(null, error);
            }
        });
    }
}
