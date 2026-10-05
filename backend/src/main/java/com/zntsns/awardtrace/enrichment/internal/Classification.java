package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;

/**
 * The model's category for one description (doc 04).
 *
 * @param confidence the model's confidence, from 0 to 1; null when it gave none, as for a refusal
 * @param reasonCode why the description is UNCLASSIFIABLE: VAGUE, REFUSAL, or FAILED; null for every other category
 * @param model the exact model ID that answered
 * @param promptVersion the prompt file it answered, such as v1
 */
record Classification(String descriptionHash, String category, BigDecimal confidence, String reasonCode, String model,
        String promptVersion) {
}
