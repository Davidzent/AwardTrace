package com.zntsns.awardtrace.pipeline.internal;

import static com.zntsns.awardtrace.pipeline.internal.TestEvents.subaward;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("pipeline")
@Import(TestcontainersConfiguration.class)
class SubawardListenerIT {

    private static final String PRIME = "CONT_AWD_SUBAWARD_LISTENER";

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    JdbcClient jdbc;

    @Test
    void storesValidSubawardsInTheNetworkAndDeadLettersInvalidOnes() throws Exception {
        var valid = subaward("LISTENER-1", PRIME, "E2QCEKQXLN48", "DVORAK, LLC", "1000.00", "2026-08-01");
        var badUei = subaward("LISTENER-2", PRIME, "E2QCEK", "DVORAK, LLC", "1.00", "2026-08-01");

        send(valid);
        send(badUei);

        await().atMost(Duration.ofSeconds(30)).until(() -> count(
                "SELECT count(*) FROM recipient_edge WHERE sub_uei = 'E2QCEKQXLN48' AND total_amount = 1000.00") == 1);
        assertThat(count("SELECT count(*) FROM subaward WHERE subaward_key LIKE 'LISTENER-%'")).isEqualTo(1);
        assertThat(deadLetterReasons(PRIME)).containsExactly("INVALID_SUB_UEI");
    }

    private void send(Object event) throws Exception {
        kafka.send(KafkaTopics.SUBAWARDS, PRIME, EventCodec.write(EventEnvelope.of(event, null))).get();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private List<String> deadLetterReasons(String key) {
        var reasons = new ArrayList<String>();
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopics.SUBAWARDS_DLT));
            Instant deadline = Instant.now().plusSeconds(20);
            while (Instant.now().isBefore(deadline) && reasons.isEmpty()) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        var header = record.headers().lastHeader(PipelineKafkaConfig.REASON_HEADER);
                        reasons.add(new String(header.value(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return reasons;
    }
}
