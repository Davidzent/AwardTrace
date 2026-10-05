package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code eval} task's files and spending limit. The paths are relative to the working directory, {@code backend/}
 * when the task runs as {@code eval/README.md} shows.
 *
 * @param goldSet the hand-labeled descriptions
 * @param reportDir where each run writes {@code {model}-{prompt version}.md}
 * @param summary the table of every run's headline numbers, beside the PSC baseline's
 * @param capUsd the most one run may spend; a request that could pass it isn't sent
 */
@ConfigurationProperties("awardtrace.enrichment.eval")
record EvalProperties(Path goldSet, Path reportDir, Path summary, BigDecimal capUsd) {
}
