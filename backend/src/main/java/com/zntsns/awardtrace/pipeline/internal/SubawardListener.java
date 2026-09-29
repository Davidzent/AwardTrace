package com.zntsns.awardtrace.pipeline.internal;

import com.zntsns.awardtrace.ingest.SubawardReported;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.KafkaTopics;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Consumes subaward events one poll batch at a time, like {@link TransactionListener}: invalid events go to
 * {@code subawards.v1.DLT} from here, and infrastructure failures are retried by the error handler.
 */
@Component
@Profile("pipeline")
class SubawardListener {

    private record Invalid(ConsumerRecord<String, String> record, InvalidEventException problem) {
    }

    private final SubawardWriter writer;
    private final DeadLetterPublishingRecoverer deadLetters;

    SubawardListener(SubawardWriter writer, DeadLetterPublishingRecoverer deadLetters) {
        this.writer = writer;
        this.deadLetters = deadLetters;
    }

    @KafkaListener(id = KafkaTopics.SUBAWARD_GROUP, topics = KafkaTopics.SUBAWARDS, batch = "true")
    void onBatch(List<ConsumerRecord<String, String>> records) {
        var subawards = new ArrayList<SubawardReported>();
        var invalid = new ArrayList<Invalid>();
        for (var record : records) {
            try {
                var event = EventCodec.read(record.value(), SubawardReported.class).payload();
                EventValidator.problem(event).ifPresentOrElse(
                        reason -> invalid.add(new Invalid(record, new InvalidEventException(reason))),
                        () -> subawards.add(event));
            } catch (JacksonException | IllegalArgumentException e) {
                invalid.add(new Invalid(record, new InvalidEventException("SCHEMA_VIOLATION", e)));
            }
        }
        writer.write(subawards);
        if (!subawards.isEmpty()) {
            // After the commit, so the refresh sees the new rows. It runs even when the write changed nothing: if a
            // refresh fails, the batch is redelivered, and that retry's write changes nothing.
            writer.refreshNetwork();
        }
        invalid.forEach(entry -> deadLetters.accept(entry.record(), entry.problem()));
    }
}
