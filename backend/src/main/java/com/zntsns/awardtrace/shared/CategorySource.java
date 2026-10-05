package com.zntsns.awardtrace.shared;

/**
 * Where the category the site shows comes from (doc 09): the free PSC baseline, or the classifier's answer. The
 * setting {@code awardtrace.categories.default-source} chooses, and it stays {@code baseline} unless the gold-set
 * evaluation shows the classifier beats the baseline (ADR 0007). The indexer and the award detail apply the same rule:
 * the classifier's category when it's the default and it named one of the 13 real categories, otherwise the baseline.
 * Changing the setting takes a reindex, since every search document carries the choice.
 */
public enum CategorySource {
    BASELINE, LLM
}
