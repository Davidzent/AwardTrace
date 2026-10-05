package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The answer contract (doc 09): the schema that structured output holds the model to, and the checks every answer
 * passes before anything from it is stored. Structured output guarantees the shape; these checks still catch a
 * missing, repeated, or unknown ID, a category outside the taxonomy, and a confidence outside 0 to 1. A problem is a
 * stable reason code, as in the pipeline's {@code EventValidator}.
 */
final class ClassificationValidation {

    /** The 14 category codes (ADR 0016), in the order {@code taxonomy_category} and the prompt list them. */
    static final List<String> CATEGORIES = List.of("IT_SOFTWARE", "IT_INFRASTRUCTURE", "CYBERSECURITY",
            "PROFESSIONAL_SERVICES", "ENGINEERING_RESEARCH", "CONSTRUCTION_FACILITIES", "HEALTH_MEDICAL",
            "DEFENSE_SYSTEMS", "LOGISTICS_TRANSPORT", "SUPPLIES_EQUIPMENT", "TRAINING_EDUCATION", "NATURAL_RESOURCES",
            "OTHER", "UNCLASSIFIABLE");

    /**
     * {@code { "items": [ { "id": string, "category": one of the 14 codes, "confidence": number } ] }}. Structured
     * output doesn't enforce a number's range, so the description states it and {@link #check} enforces it. Keys keep
     * their order, so every request sends the same schema bytes.
     */
    static final Map<String, Object> SCHEMA = object(
            "type", "object",
            "properties", object("items", object(
                    "type", "array",
                    "items", object(
                            "type", "object",
                            "properties", object(
                                    "id", object("type", "string"),
                                    "category", object("type", "string", "enum", CATEGORIES),
                                    "confidence", object("type", "number", "description", "From 0 to 1")),
                            "required", List.of("id", "category", "confidence"),
                            "additionalProperties", false))),
            "required", List.of("items"),
            "additionalProperties", false);

    private static final JsonMapper JSON = JsonMapper.shared();

    /** An answer that passed every check, or the first problem found. */
    sealed interface Checked {
    }

    /** @param answers one per input ID */
    record Valid(Map<String, Answer> answers) implements Checked {
    }

    record Rejected(String problem) implements Checked {
    }

    /** @param confidence rounded to the two decimal places {@code classification.confidence} keeps */
    record Answer(String category, BigDecimal confidence) {
    }

    record Reply(List<Item> items) {
    }

    record Item(String id, String category, BigDecimal confidence) {
    }

    private ClassificationValidation() {
    }

    /** Checks the model's JSON answer to a request that sent these IDs. */
    static Checked check(String json, List<String> ids) {
        Reply reply;
        try {
            reply = json == null ? null : JSON.readValue(json, Reply.class);
        } catch (JacksonException e) {
            return new Rejected("MALFORMED");
        }
        if (reply == null || reply.items() == null) {
            return new Rejected("MALFORMED");
        }
        var sent = new HashSet<>(ids);
        var answers = new LinkedHashMap<String, Answer>();
        for (Item item : reply.items()) {
            if (item == null || item.id() == null || item.category() == null || item.confidence() == null) {
                return new Rejected("MALFORMED");
            }
            if (!sent.contains(item.id())) {
                return new Rejected("UNKNOWN_ID");
            }
            if (answers.containsKey(item.id())) {
                return new Rejected("REPEATED_ID");
            }
            if (!CATEGORIES.contains(item.category())) {
                return new Rejected("UNKNOWN_CATEGORY");
            }
            if (item.confidence().signum() < 0 || item.confidence().compareTo(BigDecimal.ONE) > 0) {
                return new Rejected("INVALID_CONFIDENCE");
            }
            answers.put(item.id(), new Answer(item.category(), item.confidence().setScale(2, RoundingMode.HALF_UP)));
        }
        if (answers.size() < sent.size()) {
            return new Rejected("MISSING_ID");
        }
        return new Valid(answers);
    }

    /** A JSON object whose keys keep the order given. */
    private static Map<String, Object> object(Object... keysAndValues) {
        var object = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            object.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(object);
    }
}
