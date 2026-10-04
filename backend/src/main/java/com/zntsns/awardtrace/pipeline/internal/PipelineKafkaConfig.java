package com.zntsns.awardtrace.pipeline.internal;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

@Configuration(proxyBeanMethods = false)
@Profile("pipeline")
class PipelineKafkaConfig {

    static final String REASON_HEADER = "awardtrace-reason";

    /**
     * Publishes to {@code <topic>.DLT} on the record's own partition, with the standard exception and origin headers
     * plus the reason code.
     */
    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, String> kafka,
            MeterRegistry meters) {
        // Named explicitly: Spring Kafka's default suffix is "-dlt", and the broker never auto-creates topics.
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())) {

            /** Every dead letter passes here, from the listeners and the error handler; counted once it is sent. */
            @Override
            public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception exception) {
                super.accept(record, consumer, exception);
                meters.counter("awardtrace.pipeline.dlt", "reason", reasonOf(exception)).increment();
            }
        };
        recoverer.setHeadersFunction((record, exception) -> new RecordHeaders().add(new RecordHeader(REASON_HEADER,
                reasonOf(exception).getBytes(StandardCharsets.UTF_8))));
        return recoverer;
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
