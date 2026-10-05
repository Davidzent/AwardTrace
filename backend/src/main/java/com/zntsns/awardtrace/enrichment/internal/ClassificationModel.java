package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
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

    /** The tokens one request used. Thinking counts as output. */
    record Usage(long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {
    }

    /**
     * @param answer the model's JSON answer; null unless the reply is complete
     * @param usage what the request used, which is billed however the request ended
     */
    record Reply(Stop stop, String answer, Usage usage) {
    }

    /** The model ID each classification records, such as {@code claude-haiku-4-5}. */
    String model();

    /** The prompt version each classification records, such as {@code v1}. */
    String promptVersion();

    /** The most a request for these items could cost, in US dollars, so a cap can be checked before sending it. */
    BigDecimal maxCost(List<Item> items);

    Reply classify(List<Item> items);
}
