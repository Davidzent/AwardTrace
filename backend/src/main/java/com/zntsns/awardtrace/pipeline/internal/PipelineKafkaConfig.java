package com.zntsns.awardtrace.pipeline.internal;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
@Profile("pipeline")
class PipelineKafkaConfig {

    static final String REASON_HEADER = "awardtrace-reason";

    /**
     * Publishes to {@code <topic>.DLT} on the record's own partition, with the standard exception and origin headers
     * plus the reason code.
     */
    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, String> kafka) {
        // Named explicitly: Spring Kafka's default suffix is "-dlt", and the broker never auto-creates topics.
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setHeadersFunction((record, exception) -> new RecordHeaders().add(new RecordHeader(REASON_HEADER,
                reasonOf(exception).getBytes(StandardCharsets.UTF_8))));
        return recoverer;
    }

    /**
     * Invalid events never reach this handler, so whatever does is an infrastructure failure: the database or the
     * broker is down. The whole batch is retried, backing off to once a minute, for as long as it takes; nothing is
     * dead-lettered and no offset moves until the write succeeds.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        var backOff = new ExponentialBackOff(Duration.ofSeconds(1).toMillis(), 2);
        backOff.setMaxInterval(Duration.ofMinutes(1).toMillis());
        return new DefaultErrorHandler(backOff);
    }

    private static String reasonOf(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidEventException invalid) {
                return invalid.reason();
            }
        }
        return "UNKNOWN";
    }
}
