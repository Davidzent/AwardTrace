package com.zntsns.awardtrace.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.ingest.internal.StoredFilePublisher.Publication;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
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
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class StoredFilePublisherIT {

    @Autowired
    StoredFilePublisher publisher;

    @Autowired
    S3Client s3;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Test
    void publishesEveryInScopeRowAndMarksTheFilePublished() throws Exception {
        String key = "raw/contracts/all/" + UUID.randomUUID() + ".zip";
        s3.putObject(request -> request.bucket(TestcontainersConfiguration.RAW_BUCKET).key(key),
                RequestBody.fromBytes(Fixtures.zip(Fixtures.DELTA_FILE, Fixtures.csv(Fixtures.DELTA_FILE))));
        UUID runId = jdbc.sql("INSERT INTO ingest_run (mode, status) VALUES ('delta', 'running') RETURNING run_id")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                INSERT INTO ingest_file (s3_key, run_id, source_url, sha256, bytes, status)
                VALUES (:key, :runId, 'https://example.test/delta.zip', repeat('0', 64), 1, 'stored')
                """)
                .param("key", key)
                .param("runId", runId)
                .update();

        Publication publication = publisher.publish(key, runId);

        // The delta fixture holds 1 in-scope row, 2 deletes, 2 actions before FY2025, and 1 IDV.
        assertThat(publication).isEqualTo(new Publication(6, 3, 3, 0));
        assertThat(eventsFrom(key, 3))
                .extracting(event -> event.eventType() + " row " + event.source().rowNumber())
                .containsExactlyInAnyOrder(
                        "ContractTransactionIngested row 1",
                        "ContractTransactionDeleted row 2",
                        "ContractTransactionDeleted row 3");
        assertThat(jdbc.sql("SELECT status, row_count FROM ingest_file WHERE s3_key = :key")
                .param("key", key)
                .query((rs, row) -> rs.getString(1) + " " + rs.getLong(2))
                .single())
                .isEqualTo("published 6");
    }

    @SuppressWarnings("rawtypes")
    private List<EventEnvelope<Map>> eventsFrom(String s3Key, int expected) {
        var found = new ArrayList<EventEnvelope<Map>>();
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaAdmin.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopics.AWARD_TRANSACTIONS));
            Instant deadline = Instant.now().plusSeconds(20);
            while (found.size() < expected && Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    EventEnvelope<Map> envelope = EventCodec.read(record.value(), Map.class);
                    if (s3Key.equals(envelope.source().s3Key())) {
                        found.add(envelope);
                    }
                }
            }
        }
        return found;
    }
}
