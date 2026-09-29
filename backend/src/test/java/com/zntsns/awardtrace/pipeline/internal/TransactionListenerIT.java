package com.zntsns.awardtrace.pipeline.internal;

import static com.zntsns.awardtrace.pipeline.internal.TestEvents.file;
import static com.zntsns.awardtrace.pipeline.internal.TestEvents.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("pipeline")
@Import(TestcontainersConfiguration.class)
class TransactionListenerIT {

    @MockitoSpyBean
    TransactionWriter writer;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void emptyTables() {
        jdbc.sql("TRUNCATE award_transaction, award, recipient, agency, outbox").update();
    }

    @Test
    void writesValidEventsAndDeadLettersInvalidOnes() throws Exception {
        String awardId = "CONT_AWD_LISTENER_VALIDATION";
        String valid = json(transaction(awardId, "0", "2026-07-16", "27500.00", "27500.00", file("20260909")));
        String withoutUei = json(transaction(awardId, "P00001", "2026-08-24", "27500.00", "55000.00",
                file("20260909"))).replace("\"recipient_uei\":\"MN5KRX2W9R46\",", "");

        send(awardId, valid);
        send(awardId, withoutUei);

        assertThat(deadLetterReasons(awardId, 1)).containsExactly("MISSING_UEI");
        assertThat(transactionCount(awardId)).isEqualTo(1);
    }

    @Test
    void deadLettersAnUnreadableEvent() throws Exception {
        send("CONT_AWD_LISTENER_GARBLED", "{\"event_type\": \"ContractTransactionIngested\", \"payload\": [");

        assertThat(deadLetterReasons("CONT_AWD_LISTENER_GARBLED", 1)).containsExactly("SCHEMA_VIOLATION");
    }

    @Test
    void retriesADatabaseFailureInsteadOfDeadLetteringTheBatch() throws Exception {
        String awardId = "CONT_AWD_LISTENER_OUTAGE";
        doThrow(new TransientDataAccessResourceException("database down"))
                .doThrow(new TransientDataAccessResourceException("database down"))
                .doCallRealMethod()
                .when(writer).write(anyList(), anyList());

        send(awardId, json(transaction(awardId, "0", "2026-07-16", "27500.00", "27500.00", file("20260909"))));

        await().atMost(Duration.ofSeconds(30)).until(() -> transactionCount(awardId) == 1);
        verify(writer, atLeast(3)).write(anyList(), anyList());
        assertThat(deadLetterReasons(awardId, 0)).isEmpty();
    }

    private static String json(Object payload) {
        return EventCodec.write(EventEnvelope.of(payload, null));
    }

    private void send(String key, String value) throws Exception {
        kafka.send(KafkaTopics.AWARD_TRANSACTIONS, key, value).get();
    }

    private long transactionCount(String awardId) {
        return jdbc.sql("SELECT count(*) FROM award_transaction WHERE award_id = :awardId")
                .param("awardId", awardId)
                .query(Long.class)
                .single();
    }

    /** Waits for {@code expected} dead letters with this key, or for five seconds when none are expected. */
    private List<String> deadLetterReasons(String key, int expected) {
        var reasons = new ArrayList<String>();
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopics.AWARD_TRANSACTIONS_DLT));
            Instant deadline = Instant.now().plusSeconds(expected == 0 ? 5 : 20);
            while (Instant.now().isBefore(deadline) && (expected == 0 || reasons.size() < expected)) {
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
