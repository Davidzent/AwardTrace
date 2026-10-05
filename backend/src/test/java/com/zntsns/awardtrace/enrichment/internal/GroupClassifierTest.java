package com.zntsns.awardtrace.enrichment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Item;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Reply;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Stop;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Usage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GroupClassifierTest {

    private final ScriptedModel model = new ScriptedModel();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final GroupClassifier classifier = new GroupClassifier(model, meters);

    @Test
    void storesEachAnswerAndMarksVagueAnswers() {
        model.reply(items -> reply(Stop.COMPLETE, """
                {"items": [{"id": "000000000001", "category": "UNCLASSIFIABLE", "confidence": 0.4},
                           {"id": "000000000000", "category": "NATURAL_RESOURCES", "confidence": 0.85}]}"""));

        var classifications = classifier.classify(descriptions(2));

        assertThat(classifications).containsExactly(
                new Classification(hash(0), "NATURAL_RESOURCES", new BigDecimal("0.85"), null, "claude-haiku-4-5",
                        "v1"),
                new Classification(hash(1), "UNCLASSIFIABLE", new BigDecimal("0.40"), "VAGUE", "claude-haiku-4-5",
                        "v1"));
    }

    @Test
    void storesARefusedGroupAsUnclassifiableWithoutRetrying() {
        model.reply(items -> reply(Stop.REFUSAL, null));

        var classifications = classifier.classify(descriptions(3));

        assertThat(classifications).extracting(Classification::category, Classification::reasonCode)
                .containsOnly(tuple("UNCLASSIFIABLE", "REFUSAL"));
        assertThat(classifications).extracting(Classification::confidence).containsOnlyNulls();
        assertThat(model.requests()).containsExactly(3);
    }

    @Test
    void splitsATruncatedGroupInHalf() {
        model.reply(items -> reply(Stop.MAX_TOKENS, null), items -> answer(items, "OTHER"),
                items -> answer(items, "CONSTRUCTION_FACILITIES"));

        var classifications = classifier.classify(descriptions(5));

        assertThat(model.requests()).containsExactly(5, 2, 3);
        assertThat(classifications).extracting(Classification::category).containsExactly("OTHER", "OTHER",
                "CONSTRUCTION_FACILITIES", "CONSTRUCTION_FACILITIES", "CONSTRUCTION_FACILITIES");
    }

    @Test
    void retriesARejectedAnswerOnce() {
        model.reply(items -> answer(items.subList(1, items.size()), "OTHER"), items -> answer(items, "OTHER"));

        var classifications = classifier.classify(descriptions(3));

        assertThat(model.requests()).containsExactly(3, 3);
        assertThat(classifications).extracting(Classification::category).containsOnly("OTHER");
    }

    @Test
    void storesAGroupRejectedTwiceAsFailed() {
        model.reply(items -> reply(Stop.COMPLETE, "{\"items\": []}"));

        var classifications = classifier.classify(descriptions(2));

        assertThat(model.requests()).containsExactly(2, 2);
        assertThat(classifications).containsExactly(
                new Classification(hash(0), "UNCLASSIFIABLE", null, "FAILED", "claude-haiku-4-5", "v1"),
                new Classification(hash(1), "UNCLASSIFIABLE", null, "FAILED", "claude-haiku-4-5", "v1"));
    }

    @Test
    void retriesASingleDescriptionWhoseAnswerIsTruncated() {
        model.reply(items -> reply(Stop.MAX_TOKENS, null));

        var classifications = classifier.classify(descriptions(1));

        assertThat(model.requests()).containsExactly(1, 1);
        assertThat(classifications.getFirst().reasonCode()).isEqualTo("FAILED");
    }

    @Test
    void sendsGroupsOfTwentyFive() {
        model.reply(items -> answer(items, "OTHER"));

        assertThat(classifier.classify(descriptions(60))).hasSize(60);
        assertThat(model.requests()).containsExactly(25, 25, 10);
    }

    @Test
    void classifiesEachDistinctDescriptionOnce() {
        model.reply(items -> answer(items, "OTHER"));

        var classifications = classifier.classify(List.of(description(0), description(1), description(0)));

        assertThat(model.requests()).containsExactly(2);
        assertThat(classifications).extracting(Classification::descriptionHash).containsExactly(hash(0), hash(1));
    }

    @Test
    void neverSendsTwoDescriptionsUnderOneId() {
        model.reply(items -> answer(items, "OTHER"));
        String twin = hash(0).substring(0, GroupClassifier.ID_LENGTH) + "f".repeat(52);

        var classifications = classifier.classify(List.of(description(0), description(1),
                new Description(twin, "SAME ID, DIFFERENT DESCRIPTION")));

        assertThat(model.requests()).containsExactly(1, 2);
        assertThat(classifications).extracting(Classification::descriptionHash)
                .containsExactly(hash(0), hash(1), twin);
    }

    @Test
    void countsEachRequestByHowItEnded() {
        // The group of 4 is truncated; one half's answer is rejected and then accepted, and the other half is refused.
        model.reply(items -> reply(Stop.MAX_TOKENS, null), items -> reply(Stop.COMPLETE, "{\"items\": []}"),
                items -> answer(items, "OTHER"), items -> reply(Stop.REFUSAL, null));

        classifier.classify(descriptions(4));

        assertThat(calls("ok")).isEqualTo(1);
        assertThat(calls("invalid")).isEqualTo(2);
        assertThat(calls("refusal")).isEqualTo(1);
        assertThat(calls("error")).isZero();
    }

    @Test
    void countsAnErrorFromTheApiButNotARequestTheSpendControlsRefused() {
        model.reply(items -> {
            throw new IllegalStateException("Overloaded");
        }, items -> {
            throw new SpendGuard.Refused("The circuit breaker is open");
        });

        assertThatThrownBy(() -> classifier.classify(descriptions(1))).hasMessage("Overloaded");
        assertThatThrownBy(() -> classifier.classify(descriptions(1))).isInstanceOf(SpendGuard.Refused.class);

        assertThat(calls("error")).isEqualTo(1);
    }

    private double calls(String result) {
        return meters.counter("awardtrace.enricher.calls", "result", result).count();
    }

    private static Reply reply(Stop stop, String answer) {
        return new Reply(stop, answer, new Usage(0, 0, 0, 0));
    }

    /** A valid answer giving every item one category, with confidence 0.9. */
    private static Reply answer(List<Item> items, String category) {
        return reply(Stop.COMPLETE, items.stream()
                .map(item -> "{\"id\": \"" + item.id() + "\", \"category\": \"" + category + "\", \"confidence\": 0.9}")
                .collect(Collectors.joining(", ", "{\"items\": [", "]}")));
    }

    private static List<Description> descriptions(int count) {
        return IntStream.range(0, count).mapToObj(GroupClassifierTest::description).toList();
    }

    private static Description description(int n) {
        return new Description(hash(n), "DESCRIPTION " + n);
    }

    /** A 64-character hash whose first 12 characters differ from every other n's. */
    private static String hash(int n) {
        return "%012d".formatted(n) + "0".repeat(52);
    }

    /** Answers each request with the next scripted reply, repeating the last, and records each request's size. */
    private static final class ScriptedModel implements ClassificationModel {

        private final Deque<Function<List<Item>, Reply>> replies = new ArrayDeque<>();
        private final List<Integer> requests = new ArrayList<>();

        @SafeVarargs
        final void reply(Function<List<Item>, Reply>... scripted) {
            replies.addAll(List.of(scripted));
        }

        List<Integer> requests() {
            return requests;
        }

        @Override
        public String model() {
            return "claude-haiku-4-5";
        }

        @Override
        public String promptVersion() {
            return "v1";
        }

        @Override
        public BigDecimal maxCost(List<Item> items) {
            return BigDecimal.ZERO;
        }

        @Override
        public Reply classify(List<Item> items) {
            requests.add(items.size());
            var reply = replies.size() > 1 ? replies.poll() : replies.peek();
            return reply.apply(items);
        }
    }
}
