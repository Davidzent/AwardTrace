package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.ClassificationSnapshots;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Item;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Reply;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Classifies every description no classification covers, and retries each whose classification {@code FAILED}, through
 * Message Batches at half price (doc 09). Each batch
 * carries up to {@code requestsPerBatch} groups of 25, and its requests are recorded in
 * {@code enrichment_batch_request} before it's submitted. A run that stops partway, because the host stopped or the
 * task failed, leaves them there, and the next run waits for those batches and stores their results before it submits
 * anything, so no batch is paid for twice. A batch whose worst case could take the run past its cap isn't submitted.
 * The run ends by writing the classifications to S3 (ADR 0002).
 */
@Component
@Profile("enricher")
class Backfill {

    private static final Logger log = LoggerFactory.getLogger(Backfill.class);

    static final String BACKFILL = "backfill";

    /**
     * @param requests the batch requests collected, each a group of up to 25 descriptions
     * @param classified the descriptions given a classification
     * @param unanswered the descriptions of requests that errored, expired, or were canceled, left unclassified
     * @param cost what the run spent, at batch prices
     */
    record Result(int batches, int requests, int classified, int unanswered, BigDecimal cost) {

        static final Result NONE = new Result(0, 0, 0, 0, BigDecimal.ZERO);

        Result plus(Result other) {
            return new Result(batches + other.batches, requests + other.requests, classified + other.classified,
                    unanswered + other.unanswered, cost.add(other.cost));
        }
    }

    private record Recorded(String customId, String[] hashes) {
    }

    private static final String UNCLASSIFIED = """
            SELECT DISTINCT ON (a.description_hash) a.description_hash AS hash, a.description AS text
            FROM award a
            WHERE a.deleted_at IS NULL AND a.description_hash IS NOT NULL
              -- FAILED means try again: a group whose answer was cut off or didn't validate.
              AND NOT EXISTS (SELECT 1 FROM classification c
                              WHERE c.description_hash = a.description_hash AND c.reason_code IS DISTINCT FROM 'FAILED')
            ORDER BY a.description_hash, a.award_id
            """;

    private final JdbcClient jdbc;
    private final AnthropicClassificationModel claude;
    private final GroupClassifier settler;
    private final ClassificationStore store;
    private final SpendLedger ledger;
    private final ClassificationSnapshots snapshots;
    private final TransactionTemplate transactions;
    private final BackfillProperties properties;
    private final Clock clock;
    private final Prices prices;

    Backfill(JdbcClient jdbc, AnthropicClassificationModel claude, ClassificationStore store, SpendLedger ledger,
            ClassificationSnapshots snapshots, TransactionTemplate transactions, BackfillProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.claude = claude;
        this.settler = new GroupClassifier(claude);
        this.store = store;
        this.ledger = ledger;
        this.snapshots = snapshots;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
        this.prices = Prices.of(claude.model());
    }

    Result run() throws IOException, InterruptedException {
        BigDecimal cap = properties.capUsd();
        if (cap == null) {
            throw new IllegalArgumentException("Set the run's cap with --awardtrace.enrichment.backfill.cap-usd");
        }
        Result result = Result.NONE;
        for (String batchId : jdbc.sql("SELECT DISTINCT batch_id FROM enrichment_batch_request WHERE batch_id IS NOT NULL")
                .query(String.class).list()) {
            log.info("Collecting batch {}, which an earlier run submitted", batchId);
            result = result.plus(collect(batchId));
        }
        // Recorded, but the run stopped before it learned the batch's ID, if the batch was created at all.
        jdbc.sql("DELETE FROM enrichment_batch_request WHERE batch_id IS NULL").update();

        List<List<Description>> groups = groups(jdbc.sql(UNCLASSIFIED).query(Description.class).list());
        for (int start = 0; start < groups.size(); start += properties.requestsPerBatch()) {
            var requests = new LinkedHashMap<String, List<Description>>();
            String batchKey = UUID.randomUUID().toString().replace("-", "");
            List<List<Description>> batch = groups.subList(start,
                    Math.min(start + properties.requestsPerBatch(), groups.size()));
            for (int i = 0; i < batch.size(); i++) {
                requests.put(batchKey + "-" + i, batch.get(i));
            }
            BigDecimal worstCase = requests.values().stream()
                    .map(group -> claude.maxCost(items(group)).divide(BigDecimal.TWO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (result.cost().add(worstCase).compareTo(cap) > 0) {
                log.warn("Stopping with {} descriptions unclassified: the run has spent ${}, and the next batch could"
                        + " cost up to ${} more, past its cap of ${}", count(groups.subList(start, groups.size())),
                        result.cost(), worstCase, cap);
                break;
            }
            result = result.plus(submit(requests));
        }
        snapshots.write();
        return result;
    }

    /** Records the requests, submits them as one batch, and collects its results. */
    private Result submit(Map<String, List<Description>> requests) throws InterruptedException {
        requests.forEach((customId, group) -> jdbc.sql("""
                        INSERT INTO enrichment_batch_request (custom_id, description_hashes) VALUES (:customId, :hashes)
                        """)
                .param("customId", customId)
                .param("hashes", group.stream().map(Description::hash).toArray(String[]::new))
                .update());
        var items = new LinkedHashMap<String, List<Item>>();
        requests.forEach((customId, group) -> items.put(customId, items(group)));
        String batchId = claude.submitBatch(items);
        jdbc.sql("UPDATE enrichment_batch_request SET batch_id = :batchId WHERE custom_id IN (:customIds)")
                .param("batchId", batchId)
                .param("customIds", requests.keySet())
                .update();
        log.info("Submitted batch {} with {} requests", batchId, requests.size());
        return collect(batchId);
    }

    /**
     * Waits until the batch ends, then stores its classifications, adds its spend, and forgets its requests, in one
     * transaction, so a stop in between neither loses the results nor counts them twice.
     */
    private Result collect(String batchId) throws InterruptedException {
        while (!claude.batchEnded(batchId)) {
            Thread.sleep(properties.pollInterval());
        }
        Map<String, Reply> replies = claude.batchReplies(batchId);
        var requests = new LinkedHashMap<String, List<Description>>();
        jdbc.sql("SELECT custom_id, description_hashes FROM enrichment_batch_request WHERE batch_id = :batchId")
                .param("batchId", batchId)
                .query((row, n) -> new Recorded(row.getString("custom_id"),
                        (String[]) row.getArray("description_hashes").getArray()))
                .list()
                // Settling a reply needs only each description's hash, which its ID and classification come from.
                .forEach(recorded -> requests.put(recorded.customId(),
                        Arrays.stream(recorded.hashes()).map(hash -> new Description(hash, null)).toList()));
        return transactions.execute(status -> {
            var classifications = new ArrayList<Classification>();
            int unanswered = 0;
            BigDecimal cost = BigDecimal.ZERO;
            LocalDate today = LocalDate.now(clock);
            for (var request : requests.entrySet()) {
                Reply reply = replies.get(request.getKey());
                if (reply == null) {
                    unanswered += request.getValue().size();
                    continue;
                }
                BigDecimal usd = prices.batchCost(reply.usage());
                ledger.add(today, BACKFILL, reply.usage(), usd);
                cost = cost.add(usd);
                classifications.addAll(settler.settled(request.getValue(), reply));
            }
            store.store(classifications);
            jdbc.sql("DELETE FROM enrichment_batch_request WHERE batch_id = :batchId").param("batchId", batchId)
                    .update();
            log.info("Batch {}: classified {} descriptions for ${}; {} left unanswered", batchId,
                    classifications.size(), cost, unanswered);
            return new Result(1, requests.size(), classifications.size(), unanswered, cost);
        });
    }

    /**
     * Groups of up to 25 whose IDs, the first 12 characters of each hash, differ, so each answer maps to one
     * description. A description whose ID its group already holds starts the next group.
     */
    static List<List<Description>> groups(List<Description> descriptions) {
        var groups = new ArrayList<List<Description>>();
        var group = new ArrayList<Description>();
        var ids = new HashSet<String>();
        for (Description description : descriptions) {
            String id = description.hash().substring(0, GroupClassifier.ID_LENGTH);
            if (group.size() == GroupClassifier.GROUP_SIZE || ids.contains(id)) {
                groups.add(group);
                group = new ArrayList<>();
                ids.clear();
            }
            group.add(description);
            ids.add(id);
        }
        if (!group.isEmpty()) {
            groups.add(group);
        }
        return groups;
    }

    private static List<Item> items(List<Description> group) {
        return group.stream()
                .map(d -> new Item(d.hash().substring(0, GroupClassifier.ID_LENGTH), d.text()))
                .toList();
    }

    private static int count(List<List<Description>> groups) {
        return groups.stream().mapToInt(List::size).sum();
    }
}
