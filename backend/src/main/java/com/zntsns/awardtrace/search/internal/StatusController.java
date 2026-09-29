package com.zntsns.awardtrace.search.internal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.zntsns.awardtrace.award.AwardQueries;
import com.zntsns.awardtrace.ingest.IngestHistory;
import com.zntsns.awardtrace.outbox.OutboxQueries;
import com.zntsns.awardtrace.shared.KafkaTopics;
import com.zntsns.awardtrace.shared.SearchIndexes;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pipeline health for the status page (doc 07). The page polls, so the answer is kept for 15 seconds rather than
 * counting the award table and asking Elasticsearch on every request.
 */
@RestController
@Profile("api")
class StatusController {

    static final Duration TTL = Duration.ofSeconds(15);
    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    record Status(IngestHistory.Status ingest, Pipeline pipeline, Index index, Freshness freshness) {
    }

    /**
     * @param available false when Kafka didn't answer in time; lag and deadLetters are then null
     * @param lag events each consumer group has yet to read, keyed by group
     * @param deadLetters events on the dead-letter topic, within its 30-day retention
     */
    record Pipeline(boolean available, Map<String, Long> lag, Long deadLetters, OutboxQueries.Backlog outboxBacklog) {
    }

    /**
     * A document count below the award row count means the index is behind the database.
     *
     * @param available false when Elasticsearch couldn't report on the alias; its fields are then null
     */
    record Index(boolean available, String aliasTarget, Long documentCount, long awardRowCount) {
    }

    record Freshness(Instant latestSourceModifiedAt) {
    }

    private record Snapshot(Instant takenAt, Status status) {
    }

    private final IngestHistory ingest;
    private final OutboxQueries outbox;
    private final KafkaOffsets offsets;
    private final AwardQueries awards;
    private final ElasticsearchClient elasticsearch;
    private volatile Snapshot snapshot;

    StatusController(IngestHistory ingest, OutboxQueries outbox, KafkaOffsets offsets, AwardQueries awards,
            ElasticsearchClient elasticsearch) {
        this.ingest = ingest;
        this.outbox = outbox;
        this.offsets = offsets;
        this.awards = awards;
        this.elasticsearch = elasticsearch;
    }

    @GetMapping("/api/v1/status")
    ResponseEntity<Status> status() {
        var current = snapshot;
        if (current == null || current.takenAt().plus(TTL).isBefore(Instant.now())) {
            // ponytail: requests arriving together at expiry each refresh; add a lock if that load ever matters.
            current = new Snapshot(Instant.now(), read());
            snapshot = current;
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(current.status());
    }

    private Status read() {
        var live = awards.liveAwards();
        return new Status(ingest.status(), pipeline(), index(live.count()),
                new Freshness(live.latestSourceModifiedAt()));
    }

    private Pipeline pipeline() {
        var backlog = outbox.backlog();
        try {
            var lag = new LinkedHashMap<String, Long>();
            lag.put(KafkaTopics.PIPELINE_GROUP, offsets.lag(KafkaTopics.PIPELINE_GROUP, KafkaTopics.AWARD_TRANSACTIONS));
            lag.put(KafkaTopics.INDEXER_GROUP, offsets.lag(KafkaTopics.INDEXER_GROUP, KafkaTopics.AWARDS_CHANGED));
            return new Pipeline(true, lag, offsets.retained(KafkaTopics.AWARD_TRANSACTIONS_DLT), backlog);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Kafka couldn't report consumer lag", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Pipeline(false, null, null, backlog);
    }

    /** The status page must still answer when Elasticsearch doesn't, since that is what it's there to show. */
    private Index index(long awardRowCount) {
        try {
            var aliases = elasticsearch.indices().getAlias(request -> request.name(SearchIndexes.AWARDS));
            long documents = elasticsearch.count(request -> request.index(SearchIndexes.AWARDS)).count();
            return new Index(true, String.join(",", aliases.aliases().keySet()), documents, awardRowCount);
        } catch (IOException | ElasticsearchException e) {
            log.warn("Elasticsearch couldn't report on the {} alias", SearchIndexes.AWARDS, e);
            return new Index(false, null, null, awardRowCount);
        }
    }
}
