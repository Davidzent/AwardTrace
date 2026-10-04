package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.KafkaTopics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Consumes transaction events one poll batch at a time. Invalid events go to the dead-letter topic from here,
 * never through the error handler, which retries infrastructure failures instead (see {@code KafkaListenerConfig}).
 * Offsets are committed after this method returns, so after the database commit.
 */
@Component
@Profile("pipeline")
class TransactionListener {

    private static final Map<String, Class<?>> PAYLOAD_TYPES = Map.of(
            "ContractTransactionIngested", ContractTransactionIngested.class,
            "ContractTransactionDeleted", ContractTransactionDeleted.class);

    private record Invalid(ConsumerRecord<String, String> record, InvalidEventException problem) {
    }

    private final TransactionWriter writer;
    private final DeadLetterPublishingRecoverer deadLetters;
    // How often duplicate or out-of-order events arrive; stale counts the version guard at work (doc 11).
    private final Counter insertedUpserts;
    private final Counter updatedUpserts;
    private final Counter staleUpserts;

    TransactionListener(TransactionWriter writer, DeadLetterPublishingRecoverer deadLetters, MeterRegistry meters) {
        this.writer = writer;
        this.deadLetters = deadLetters;
        this.insertedUpserts = meters.counter("awardtrace.pipeline.upserts", "outcome", "inserted");
        this.updatedUpserts = meters.counter("awardtrace.pipeline.upserts", "outcome", "updated");
        this.staleUpserts = meters.counter("awardtrace.pipeline.upserts", "outcome", "stale");
    }

    @KafkaListener(id = KafkaTopics.PIPELINE_GROUP, topics = KafkaTopics.AWARD_TRANSACTIONS, batch = "true")
    void onBatch(List<ConsumerRecord<String, String>> records) {
        var ingested = new ArrayList<ContractTransactionIngested>();
        var deleted = new ArrayList<ContractTransactionDeleted>();
        var invalid = new ArrayList<Invalid>();
        for (var record : records) {
            try {
                switch (EventCodec.read(record.value(), PAYLOAD_TYPES).payload()) {
                    case ContractTransactionIngested event -> EventValidator.problem(event).ifPresentOrElse(
                            reason -> invalid.add(new Invalid(record, new InvalidEventException(reason))),
                            () -> ingested.add(event));
                    case ContractTransactionDeleted event -> EventValidator.problem(event).ifPresentOrElse(
                            reason -> invalid.add(new Invalid(record, new InvalidEventException(reason))),
                            () -> deleted.add(event));
                    default -> throw new IllegalStateException("No handler for " + record.value());
                }
            } catch (JacksonException | IllegalArgumentException e) {
                invalid.add(new Invalid(record, new InvalidEventException("SCHEMA_VIOLATION", e)));
            }
        }
        var result = writer.write(ingested, deleted);
        // After the commit, so a batch that rolls back and is redelivered counts once.
        insertedUpserts.increment(result.inserted());
        updatedUpserts.increment(result.updated());
        staleUpserts.increment(result.stale());
        // After the database commit: if a send fails, the batch is redelivered, the write is a no-op, and the
        // dead letters are sent again.
        invalid.forEach(entry -> deadLetters.accept(entry.record(), entry.problem()));
    }
}
