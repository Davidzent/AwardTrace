package com.zntsns.awardtrace.enrichment.internal;

import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Item;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Reply;
import com.zntsns.awardtrace.enrichment.internal.ClassificationModel.Stop;
import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Answer;
import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Checked;
import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Rejected;
import com.zntsns.awardtrace.enrichment.internal.ClassificationValidation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies descriptions in groups of 25, one request a group, and decides what each way a request ends means
 * (doc 09):
 *
 * <ul>
 *   <li>A refusal makes the whole group {@code UNCLASSIFIABLE} with reason {@code REFUSAL}, and is never retried.
 *   <li>A truncated answer splits the group in half, and each half is classified as a group of its own. A single
 *       description has no half, so its truncated answer counts as rejected.
 *   <li>A rejected answer is retried once. A second rejection makes the group {@code UNCLASSIFIABLE} with reason
 *       {@code FAILED}, for {@code enrich-retry-failed} to reprocess.
 *   <li>A valid answer gives each description its category. {@code UNCLASSIFIABLE} gets reason {@code VAGUE}.
 * </ul>
 *
 * An error from the API isn't an answer. It propagates, and its descriptions stay unclassified.
 */
class GroupClassifier {

    private static final Logger log = LoggerFactory.getLogger(GroupClassifier.class);

    /** Spreads the system prompt across many descriptions while keeping each answer short (doc 09). */
    static final int GROUP_SIZE = 25;

    /** Each description is sent and answered under the first 12 characters of its hash. */
    static final int ID_LENGTH = 12;

    private final ClassificationModel model;

    GroupClassifier(ClassificationModel model) {
        this.model = model;
    }

    /** One classification for each distinct description hash, in the order the descriptions came. */
    List<Classification> classify(List<Description> descriptions) {
        var byHash = new LinkedHashMap<String, Description>();
        descriptions.forEach(description -> byHash.putIfAbsent(description.hash(), description));
        var distinct = List.copyOf(byHash.values());
        var classifications = new ArrayList<Classification>();
        for (int start = 0; start < distinct.size(); start += GROUP_SIZE) {
            classifications.addAll(group(distinct.subList(start, Math.min(start + GROUP_SIZE, distinct.size()))));
        }
        return classifications;
    }

    private List<Classification> group(List<Description> group) {
        // Two hashes that share an ID can't go in one request; halving parts them, at worst into single descriptions.
        if (group.stream().map(GroupClassifier::id).distinct().count() < group.size()) {
            return halves(group);
        }
        return attempt(group, false);
    }

    private List<Classification> attempt(List<Description> group, boolean retry) {
        Reply reply = model.classify(group.stream().map(d -> new Item(id(d), d.text())).toList());
        if (reply.stop() == Stop.REFUSAL) {
            return unclassifiable(group, "REFUSAL");
        }
        if (reply.stop() == Stop.MAX_TOKENS && group.size() > 1) {
            return halves(group);
        }
        return switch (checked(group, reply)) {
            case Valid valid -> answered(group, valid);
            case Rejected(String problem) -> {
                log.warn("Rejected the answer for {} descriptions: {}", group.size(), problem);
                yield retry ? unclassifiable(group, "FAILED") : attempt(group, true);
            }
        };
    }

    /**
     * What a reply means when there's no second attempt, as for a Message Batch request: a refusal makes the group
     * {@code REFUSAL}, a valid answer gives each description its category, and anything else makes the group
     * {@code FAILED}, for {@code enrich-retry-failed} to reprocess.
     */
    List<Classification> settled(List<Description> group, Reply reply) {
        if (reply.stop() == Stop.REFUSAL) {
            return unclassifiable(group, "REFUSAL");
        }
        return switch (checked(group, reply)) {
            case Valid valid -> answered(group, valid);
            case Rejected(String problem) -> {
                log.warn("Rejected the answer for {} descriptions: {}", group.size(), problem);
                yield unclassifiable(group, "FAILED");
            }
        };
    }

    private static Checked checked(List<Description> group, Reply reply) {
        return reply.stop() == Stop.COMPLETE
                ? ClassificationValidation.check(reply.answer(), group.stream().map(GroupClassifier::id).toList())
                : new Rejected("TRUNCATED");
    }

    private List<Classification> halves(List<Description> group) {
        int middle = group.size() / 2;
        var classifications = new ArrayList<>(group(group.subList(0, middle)));
        classifications.addAll(group(group.subList(middle, group.size())));
        return classifications;
    }

    private List<Classification> answered(List<Description> group, Valid valid) {
        return group.stream().map(d -> {
            Answer answer = valid.answers().get(id(d));
            String reason = answer.category().equals("UNCLASSIFIABLE") ? "VAGUE" : null;
            return new Classification(d.hash(), answer.category(), answer.confidence(), reason, model.model(),
                    model.promptVersion());
        }).toList();
    }

    private List<Classification> unclassifiable(List<Description> group, String reason) {
        return group.stream()
                .map(d -> new Classification(d.hash(), "UNCLASSIFIABLE", null, reason, model.model(),
                        model.promptVersion()))
                .toList();
    }

    private static String id(Description description) {
        return description.hash().substring(0, ID_LENGTH);
    }
}
