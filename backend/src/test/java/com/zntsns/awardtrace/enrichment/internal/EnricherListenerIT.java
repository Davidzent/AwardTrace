package com.zntsns.awardtrace.enrichment.internal;

import static com.zntsns.awardtrace.AwardRows.describe;
import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.EventEnvelope;
import com.zntsns.awardtrace.shared.KafkaTopics;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The live path, from award-changed events on Kafka to stored categories (doc 09). */
@SpringBootTest
@ActiveProfiles("enricher")
@Import({TestcontainersConfiguration.class, FakeClaudeConfiguration.class})
class EnricherListenerIT {

    private static final String CHANGED = "6".repeat(64);
    private static final String ANNOUNCED = "7".repeat(64);
    // A fetch waits up to 5 seconds for more events, so each step gets several times that.
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    FakeClaude claude;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void reset() {
        AwardRows.emptyTables(jdbc);
        claude.reset();
    }

    @Test
    void classifiesTheDescriptionsOfChangedAwards() {
        saveAward(jdbc, "CONT_AWD_CHANGED", 2, "1000.00", false);
        describe(jdbc, CHANGED, "CONT_AWD_CHANGED");

        publish("CONT_AWD_CHANGED", "TRANSACTION");

        await().atMost(WAIT).until(() -> categoryOf(CHANGED) != null);
        assertThat(claude.requests()).containsExactly(List.of(id(CHANGED)));
    }

    @Test
    void skipsTheEventsItsOwnClassificationsAnnounce() {
        saveAward(jdbc, "CONT_AWD_ANNOUNCED", 2, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_CHANGED", 2, "1000.00", false);
        describe(jdbc, ANNOUNCED, "CONT_AWD_ANNOUNCED");
        describe(jdbc, CHANGED, "CONT_AWD_CHANGED");

        // One partition keeps them in order, so once the second is classified, the first was read and skipped.
        publish("CONT_AWD_ANNOUNCED", "CLASSIFICATION");
        publish("CONT_AWD_CHANGED", "TRANSACTION");

        await().atMost(WAIT).until(() -> categoryOf(CHANGED) != null);
        assertThat(categoryOf(ANNOUNCED)).isNull();
        assertThat(claude.requests()).containsExactly(List.of(id(CHANGED)));
    }

    @Test
    void keepsListeningAfterTheModelFails() {
        saveAward(jdbc, "CONT_AWD_CHANGED", 2, "1000.00", false);
        describe(jdbc, CHANGED, "CONT_AWD_CHANGED");
        claude.fail(true);

        publish("CONT_AWD_CHANGED", "TRANSACTION");
        await().atMost(WAIT).until(() -> !claude.requests().isEmpty());
        claude.fail(false);
        publish("CONT_AWD_CHANGED", "TRANSACTION");

        await().atMost(WAIT).until(() -> categoryOf(CHANGED) != null);
        assertThat(claude.requests()).containsExactly(List.of(id(CHANGED)), List.of(id(CHANGED)));
    }

    private void publish(String awardId, String changeReason) {
        var event = EventEnvelope.of(new AwardChanged(awardId, 3, changeReason), null);
        kafka.send(new ProducerRecord<>(KafkaTopics.AWARDS_CHANGED, 0, awardId, EventCodec.write(event))).join();
    }

    private String categoryOf(String hash) {
        return jdbc.sql("SELECT category FROM classification WHERE description_hash = :hash")
                .param("hash", hash)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static String id(String hash) {
        return hash.substring(0, GroupClassifier.ID_LENGTH);
    }
}
