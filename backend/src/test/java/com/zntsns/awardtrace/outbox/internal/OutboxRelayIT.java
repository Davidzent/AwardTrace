package com.zntsns.awardtrace.outbox.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** The scheduled relay is slowed to once an hour, so each test drives it by calling {@code relay()} itself. */
@SpringBootTest(properties = "awardtrace.outbox.relay-delay=1h")
@ActiveProfiles("pipeline")
@Import(TestcontainersConfiguration.class)
class OutboxRelayIT {

    @Autowired
    OutboxRelay relay;

    // Raw, to match the auto-configured bean, which Spring Boot declares as KafkaTemplate<?, ?>.
    @MockitoSpyBean
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafka;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MeterRegistry meters;

    @AfterEach
    void emptyOutbox() {
        jdbc.sql("TRUNCATE outbox").update();
    }

    @Test
    void publishesPendingRowsKeyedByAwardAndMarksThemPublished() {
        List<UUID> eventIds = List.of(
                insertRow("CONT_AWD_RELAY_A", 1),
                insertRow("CONT_AWD_RELAY_B", 1),
                insertRow("CONT_AWD_RELAY_A", 2));
        assertThat(unpublishedGauge()).isEqualTo(3);

        relay.relay();

        List<EventEnvelope<AwardChanged>> events = changedEvents(eventIds);
        assertThat(events).extracting(EventEnvelope::eventId).containsExactlyInAnyOrderElementsOf(eventIds);
        assertThat(events).extracting(EventEnvelope::payload)
                .filteredOn(event -> event.awardId().equals("CONT_AWD_RELAY_A"))
                .extracting(AwardChanged::indexVersion)
                .containsExactly(1L, 2L);
        assertThat(unpublishedRows()).isZero();
        assertThat(unpublishedGauge()).isZero();
    }

    @Test
    void leavesRowsUnpublishedWhileTheBrokerFailsAndSendsThemOnceItRecovers() {
        insertRow("CONT_AWD_RELAY_OUTAGE", 1);
        doReturn(CompletableFuture.failedFuture(new KafkaException("broker down")))
                .when(kafka).send(eq(KafkaTopics.AWARDS_CHANGED), anyString(), anyString());

        assertThatThrownBy(relay::relay).hasRootCauseMessage("broker down");
        assertThat(unpublishedRows()).isEqualTo(1);

        reset(kafka);
        relay.relay();

        assertThat(unpublishedRows()).isZero();
    }

    private UUID insertRow(String awardId, long indexVersion) {
        return jdbc.sql("""
                INSERT INTO outbox (aggregate_id, event_type, change_reason, index_version)
                VALUES (:awardId, 'AwardChanged', 'TRANSACTION', :indexVersion)
                RETURNING event_id
                """)
                .param("awardId", awardId)
                .param("indexVersion", indexVersion)
                .query(UUID.class)
                .single();
    }

    private double unpublishedGauge() {
        return meters.get("awardtrace.outbox.unpublished").gauge().value();
    }

    private long unpublishedRows() {
        return jdbc.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Long.class).single();
    }

    /** The events with these IDs, read from the topic; other tests' events on it are ignored. */
    private List<EventEnvelope<AwardChanged>> changedEvents(List<UUID> eventIds) {
        var events = new ArrayList<EventEnvelope<AwardChanged>>();
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopics.AWARDS_CHANGED));
            Instant deadline = Instant.now().plusSeconds(20);
            while (events.size() < eventIds.size() && Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    var event = EventCodec.read(record.value(), AwardChanged.class);
                    if (eventIds.contains(event.eventId())) {
                        assertThat(record.key()).isEqualTo(event.payload().awardId());
                        events.add(event);
                    }
                }
            }
        }
        return events;
    }
}
