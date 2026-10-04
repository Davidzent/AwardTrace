package com.zntsns.awardtrace.outbox.internal;

import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.outbox.OutboxQueries;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to {@code awards.changed.v1} (ADR 0003). A row is marked published only after the broker
 * acknowledges it, in the same transaction that locked it; if anything fails, the transaction rolls back and the
 * row is sent again. Consumers are idempotent, so a repeat is harmless. {@code SKIP LOCKED} lets a second relay run
 * without sending the same rows at the same time.
 */
@Component
@Profile("pipeline")
class OutboxRelay {

    static final int BATCH_SIZE = 500;

    private record Row(long id, UUID eventId, String aggregateId, String changeReason, long indexVersion,
            OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transactions,
            OutboxQueries outbox, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transactions = transactions;
        // Is the relay keeping up? Counted when Prometheus scrapes, over the partial index of unpublished rows.
        Gauge.builder("awardtrace.outbox.unpublished", outbox, queries -> queries.backlog().count()).register(meters);
    }

    /**
     * Relays until the outbox is drained, one transaction per batch, so a backfill isn't held to one batch a tick. The
     * first run waits one delay too: without it, Spring runs the task at startup, and a test that sets a long delay to
     * call the relay itself would race that run.
     */
    @Scheduled(initialDelayString = "${awardtrace.outbox.relay-delay:500ms}",
            fixedDelayString = "${awardtrace.outbox.relay-delay:500ms}")
    void relay() {
        while (transactions.execute(status -> relayBatch()) == BATCH_SIZE) {
            // A full batch means more rows are probably waiting.
        }
    }

    private int relayBatch() {
        List<Row> rows = jdbc.sql("""
                SELECT id, event_id, aggregate_id, change_reason, index_version, created_at
                FROM outbox
                WHERE published_at IS NULL
                ORDER BY id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("limit", BATCH_SIZE)
                .query(Row.class)
                .list();
        if (rows.isEmpty()) {
            return 0;
        }
        var sends = rows.stream()
                .map(row -> kafka.send(KafkaTopics.AWARDS_CHANGED, row.aggregateId(), EventCodec.write(envelope(row))))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(sends).join();
        jdbc.sql("UPDATE outbox SET published_at = now() WHERE id IN (:ids)")
                .param("ids", rows.stream().map(Row::id).toList())
                .update();
        return rows.size();
    }

    private static EventEnvelope<AwardChanged> envelope(Row row) {
        var payload = new AwardChanged(row.aggregateId(), row.indexVersion(), row.changeReason());
        return new EventEnvelope<>(row.eventId(), "AwardChanged", 1, row.createdAt().toInstant(), null, payload);
    }
}
