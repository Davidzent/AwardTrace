package com.zntsns.awardtrace.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * The shape of every event on every topic. {@code eventType} is the payload's simple class name.
 *
 * @param occurredAt when AwardTrace produced the event, not when the source changed
 * @param source where an ingest event's row came from; {@code null} on other events
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        Source source,
        T payload) {

    public static <T> EventEnvelope<T> of(T payload, Source source) {
        return new EventEnvelope<>(UuidV7.next(), payload.getClass().getSimpleName(), 1, Instant.now(), source, payload);
    }

    public record Source(UUID runId, String s3Key, long rowNumber) {
    }
}
