package com.zntsns.awardtrace.enrichment.internal;

import java.util.List;

/**
 * One request to a model: one category for each item. Tests use a stub, so no test calls the Claude API (doc 09).
 */
interface ClassificationModel {

    /** @param id the first 12 characters of the description hash, which the answer must repeat */
    record Item(String id, String text) {
    }

    /** How the model stopped. Only a complete reply has an answer worth checking. */
    enum Stop {
        COMPLETE, MAX_TOKENS, REFUSAL
    }

    /** @param answer the model's JSON answer; null unless the reply is complete */
    record Reply(Stop stop, String answer) {
    }

    /** The model ID each classification records, such as {@code claude-haiku-4-5}. */
    String model();

    /** The prompt version each classification records, such as {@code v1}. */
    String promptVersion();

    Reply classify(List<Item> items);
}
