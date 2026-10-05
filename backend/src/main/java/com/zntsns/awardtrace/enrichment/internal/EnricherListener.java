package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.outbox.AwardChanged;
import com.zntsns.awardtrace.shared.EventCodec;
import com.zntsns.awardtrace.shared.KafkaTopics;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * The live path (doc 09): classifies the descriptions of awards as they change. One consumer, since the model's rate
 * limits bound it, not Kafka. While fewer than 16 KB of events wait, Kafka holds each fetch for up to 5 seconds, so a
 * trickle of changes still fills groups of 25. Events with reason {@code CLASSIFICATION} are skipped: they announce
 * categories the enricher itself just stored.
 *
 * <p>Enrichment only adds, so the pipeline never waits on the model. A batch the spend controls refuse, or that fails,
 * is logged and dropped; its descriptions stay unclassified until {@code enrich-backfill} or a later change picks them
 * up. Tasks such as {@code eval} run with the {@code enricher} profile too, so this listens only when no task is set.
 */
@Component
@Profile("enricher")
@ConditionalOnProperty(name = "awardtrace.enrichment.task", havingValue = "live", matchIfMissing = true)
class EnricherListener {

    private static final Logger log = LoggerFactory.getLogger(EnricherListener.class);

    private final Enricher enricher;

    EnricherListener(Enricher enricher) {
        this.enricher = enricher;
    }

    @KafkaListener(id = KafkaTopics.ENRICHER_GROUP, topics = KafkaTopics.AWARDS_CHANGED, batch = "true",
            concurrency = "1", properties = {"fetch.min.bytes=16384", "fetch.max.wait.ms=5000"})
    void onBatch(List<ConsumerRecord<String, String>> records) {
        var awardIds = new LinkedHashSet<String>();
        for (var record : records) {
            try {
                AwardChanged event = EventCodec.read(record.value(), AwardChanged.class).payload();
                if (!"CLASSIFICATION".equals(event.changeReason())) {
                    awardIds.add(event.awardId());
                }
            } catch (JacksonException e) {
                // The outbox relay writes these events, so an unreadable one is a bug to fix, not data to retry.
                log.error("Skipping unreadable award-changed event at offset {}: {}", record.offset(), record.value(), e);
            }
        }
        try {
            Enricher.Result result = enricher.enrich(awardIds);
            if (result.classified() > 0) {
                log.info("Classified {} descriptions for {} awards; {} were already classified", result.classified(),
                        awardIds.size(), result.cached());
            }
        } catch (SpendGuard.Refused e) {
            log.warn("Left the descriptions of {} awards unclassified: {}", awardIds.size(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Classifying the descriptions of {} awards failed; they stay unclassified", awardIds.size(), e);
        }
    }
}
