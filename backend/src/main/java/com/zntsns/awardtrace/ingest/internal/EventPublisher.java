package com.zntsns.awardtrace.ingest.internal;

import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Publishes source events keyed by award ID, so every event for one award lands on one partition in order
 * (ADR 0004): a transaction by its award, a subaward by its prime award. Callers wait on the returned futures before
 * treating a file as published.
 */
@Component
class EventPublisher {

    private final KafkaTemplate<String, String> kafka;

    EventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    CompletableFuture<SendResult<String, String>> publish(String topic, String awardId, Object payload,
            EventEnvelope.Source source) {
        return kafka.send(topic, awardId, EventCodec.write(EventEnvelope.of(payload, source)));
    }

    /** Blocks until every event sent so far is acknowledged or has failed, and its future has completed. */
    void flush() {
        kafka.flush();
    }
}
