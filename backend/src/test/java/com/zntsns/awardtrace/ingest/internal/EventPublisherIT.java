package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.ingest.ContractTransactionDeleted;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.UuidV7;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventPublisherIT {

    @Autowired
    EventPublisher publisher;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Test
    void publishesTheEnvelopeKeyedByAwardId() throws Exception {
        var deleted = new ContractTransactionDeleted("12K3_-NONE-_12639526P0220_0_-NONE-_0",
                "CONT_AWD_12639526P0220_12K3_-NONE-_-NONE-", "FY(All)_012_Contracts_Delta_20260908_1.csv",
                LocalDate.of(2026, 9, 8));
        var source = new EventEnvelope.Source(UuidV7.next(), "raw/contracts/2026/abc.zip", 2);

        var sent = publisher.publish(KafkaTopics.AWARD_TRANSACTIONS, deleted.awardId(), deleted, source).get();

        try (var consumer = consumer()) {
            var partition = new TopicPartition(KafkaTopics.AWARD_TRANSACTIONS, sent.getRecordMetadata().partition());
            consumer.assign(List.of(partition));
            consumer.seek(partition, sent.getRecordMetadata().offset());
            var record = consumer.poll(Duration.ofSeconds(10)).iterator().next();

            assertThat(record.key()).isEqualTo(deleted.awardId());
            var envelope = EventCodec.read(record.value(), ContractTransactionDeleted.class);
            assertThat(envelope.eventType()).isEqualTo("ContractTransactionDeleted");
            assertThat(envelope.source()).isEqualTo(source);
            assertThat(envelope.payload()).isEqualTo(deleted);
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        return new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer());
    }
}
