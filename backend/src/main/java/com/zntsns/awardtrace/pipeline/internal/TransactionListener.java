package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.ingest.ContractTransactionIngested;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.KafkaTopics;
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
 * never through the error handler, which retries infrastructure failures instead (see {@link PipelineKafkaConfig}).
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

    TransactionListener(TransactionWriter writer, DeadLetterPublishingRecoverer deadLetters) {
        this.writer = writer;
        this.deadLetters = deadLetters;
    }

    @KafkaListener(id = "pipeline", topics = KafkaTopics.AWARD_TRANSACTIONS, batch = "true")
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
        writer.write(ingested, deleted);
        // After the database commit: if a send fails, the batch is redelivered, the write is a no-op, and the
        // dead letters are sent again.
        invalid.forEach(entry -> deadLetters.accept(entry.record(), entry.problem()));
    }
}
