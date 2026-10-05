package com.zntsns.awardtrace.enrichment.internal;

import static com.zntsns.awardtrace.AwardRows.describe;
import static com.zntsns.awardtrace.AwardRows.saveAward;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.AwardRows;
import com.zntsns.awardtrace.TestcontainersConfiguration;
import com.zntsns.awardtrace.enrichment.ClassificationSnapshots;
import com.zntsns.awardtrace.enrichment.internal.Backfill.Result;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Item;
import com.zntsns.awardtrace.enrichment.internal.FakeClaude.BatchResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/** The backfill path, through Message Batches at half price (doc 09). */
@SpringBootTest
@ActiveProfiles("enricher")
@Import({TestcontainersConfiguration.class, FakeClaudeConfiguration.class})
class BackfillIT {

    private static final String FIRST = "1".repeat(64);
    private static final String SECOND = "2".repeat(64);
    private static final String CLASSIFIED = "3".repeat(64);

    @Autowired
    JdbcClient jdbc;

    @Autowired
    FakeClaude claude;

    @Autowired
    AnthropicClassificationModel model;

    @Autowired
    ClassificationStore store;

    @Autowired
    SpendLedger ledger;

    @Autowired
    ClassificationSnapshots snapshots;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    Clock clock;

    @Autowired
    S3Client s3;

    @AfterEach
    void reset() {
        AwardRows.emptyTables(jdbc);
        claude.reset();
        // A snapshot that holds more classifications than the table blocks the next write, so each test starts clean.
        snapshotKeys().forEach(key -> s3.deleteObject(request -> request
                .bucket(TestcontainersConfiguration.RAW_BUCKET).key(key)));
    }

    @Test
    void classifiesEachUnclassifiedDescriptionOnceInHalfPriceBatches() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_B", 1, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_C", 1, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_D", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A", "CONT_AWD_B");
        describe(jdbc, SECOND, "CONT_AWD_C");
        describe(jdbc, CLASSIFIED, "CONT_AWD_D");
        store.store(List.of(new Classification(CLASSIFIED, "OTHER", new BigDecimal("0.80"), null, "claude-haiku-4-5",
                "v1")));

        Result result = backfill("1.00", 200).run();

        // FakeClaude bills 1,000 input and 60 output tokens for two items: $0.0013, halved for a batch.
        assertThat(result).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(new Result(1, 1, 2, 0, new BigDecimal("0.00065")));
        assertThat(claude.batches()).singleElement()
                .satisfies(batch -> assertThat(batch.requests().values()).containsExactly(List.of(id(FIRST), id(SECOND))));
        assertThat(claude.requests()).as("requests outside a batch").isEmpty();
        assertThat(categories()).containsExactly(FIRST + " OTHER", SECOND + " OTHER", CLASSIFIED + " OTHER");
        assertThat(jdbc.sql("""
                SELECT path || ' ' || requests || ' ' || input_tokens || ' ' || output_tokens || ' ' || usd
                FROM enrichment_spend
                """).query(String.class).single()).isEqualTo("backfill 1 1000 60 0.000650");
        assertThat(pendingRequests()).isZero();
        assertThat(snapshotKeys()).hasSize(1);
    }

    @Test
    void retriesDescriptionsWhoseClassificationFailedButNeverRefusals() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_B", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A");
        describe(jdbc, SECOND, "CONT_AWD_B");
        store.store(List.of(
                new Classification(FIRST, "UNCLASSIFIABLE", null, "FAILED", "claude-haiku-4-5", "v1"),
                new Classification(SECOND, "UNCLASSIFIABLE", null, "REFUSAL", "claude-haiku-4-5", "v1")));

        backfill("1.00", 200).run();

        assertThat(claude.batches()).singleElement()
                .satisfies(batch -> assertThat(batch.requests().values()).containsExactly(List.of(id(FIRST))));
        assertThat(categories()).containsExactly(FIRST + " OTHER", SECOND + " UNCLASSIFIABLE");
    }

    @Test
    void submitsTheGroupsInBatchesOfTheConfiguredSize() throws Exception {
        // 26 descriptions make a group of 25 and a group of 1.
        IntStream.range(0, 26).forEach(i -> {
            saveAward(jdbc, "CONT_AWD_" + i, 1, "1000.00", false);
            describe(jdbc, "%02d".formatted(i).repeat(32), "CONT_AWD_" + i);
        });

        Result result = backfill("1.00", 1).run();

        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.classified()).isEqualTo(26);
        assertThat(claude.batches()).extracting(batch -> batch.requests().size()).containsExactly(1, 1);
    }

    @Test
    void submitsNothingWhoseWorstCaseCouldPassTheCap() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A");

        Result result = backfill("0.001", 200).run();

        assertThat(result).isEqualTo(Result.NONE);
        assertThat(claude.batches()).isEmpty();
        assertThat(categories()).isEmpty();
    }

    @Test
    void collectsABatchThatAnEarlierRunSubmittedBeforeSubmittingAnother() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A");
        // An earlier run recorded and submitted this batch, then stopped before collecting it.
        String batchId = model.submitBatch(Map.of("earlier-0", List.of(new Item(id(FIRST), "Firewood"))));
        jdbc.sql("""
                INSERT INTO enrichment_batch_request (custom_id, batch_id, description_hashes)
                VALUES ('earlier-0', :batchId, ARRAY[:hash])
                """).param("batchId", batchId).param("hash", FIRST).update();

        Result result = backfill("1.00", 200).run();

        assertThat(result.batches()).isEqualTo(1);
        assertThat(claude.batches()).as("no second batch for the same description").hasSize(1);
        assertThat(categories()).containsExactly(FIRST + " OTHER");
        assertThat(pendingRequests()).isZero();
    }

    @Test
    void leavesTheDescriptionsOfErroredRequestsUnclassifiedAndUnbilled() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A");
        claude.endBatchRequests(BatchResult.ERRORED);

        Result result = backfill("1.00", 200).run();

        assertThat(result).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(new Result(1, 1, 0, 1, BigDecimal.ZERO));
        assertThat(categories()).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM enrichment_spend").query(Long.class).single()).isZero();
        assertThat(pendingRequests()).isZero();
    }

    @Test
    void storesARefusedGroupAsUnclassifiable() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A");
        claude.endBatchRequests(BatchResult.REFUSED);

        backfill("1.00", 200).run();

        assertThat(reasons()).containsExactly(FIRST + " REFUSAL");
    }

    @Test
    void failsAGroupWhoseAnswerMissesADescription() throws Exception {
        saveAward(jdbc, "CONT_AWD_A", 1, "1000.00", false);
        saveAward(jdbc, "CONT_AWD_B", 1, "1000.00", false);
        describe(jdbc, FIRST, "CONT_AWD_A");
        describe(jdbc, SECOND, "CONT_AWD_B");
        claude.endBatchRequests(BatchResult.MISSING_AN_ID);

        backfill("1.00", 200).run();

        assertThat(reasons()).containsExactly(FIRST + " FAILED", SECOND + " FAILED");
    }

    @Test
    void groupsUpTo25DescriptionsWhoseIdsDiffer() {
        List<Description> descriptions = IntStream.range(0, 27)
                .mapToObj(i -> new Description("%02d".formatted(i).repeat(32), "Item " + i))
                .toList();
        // The same first 12 characters as the second description, so it can't share that description's group.
        var twin = new Description("01".repeat(6) + "ff".repeat(26), "Twin");

        var groups = Backfill.groups(List.of(descriptions.get(0), descriptions.get(1), twin, descriptions.get(2)));

        assertThat(Backfill.groups(descriptions)).extracting(List::size).containsExactly(25, 2);
        assertThat(groups).extracting(List::size).containsExactly(2, 2);
    }

    private Backfill backfill(String capUsd, int requestsPerBatch) {
        return new Backfill(jdbc, model, store, ledger, snapshots, transactions,
                new BackfillProperties(new BigDecimal(capUsd), requestsPerBatch, Duration.ofMillis(10)), clock);
    }

    private List<String> categories() {
        return jdbc.sql("SELECT description_hash || ' ' || category FROM classification ORDER BY description_hash")
                .query(String.class).list();
    }

    private List<String> reasons() {
        return jdbc.sql("SELECT description_hash || ' ' || reason_code FROM classification ORDER BY description_hash")
                .query(String.class).list();
    }

    private long pendingRequests() {
        return jdbc.sql("SELECT count(*) FROM enrichment_batch_request").query(Long.class).single();
    }

    private List<String> snapshotKeys() {
        return s3.listObjectsV2Paginator(request -> request.bucket(TestcontainersConfiguration.RAW_BUCKET)
                        .prefix(ClassificationSnapshots.FOLDER))
                .contents().stream().map(S3Object::key).toList();
    }

    private static String id(String hash) {
        return hash.substring(0, GroupClassifier.ID_LENGTH);
    }
}
