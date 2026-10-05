package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code eval} task's files and spending limit. The paths are relative to the working directory, {@code backend/}
 * when the task runs as {@code eval/README.md} shows. They bind as text: bound to {@link Path}, a relative path
 * resolves against the web server's root wherever the API runs too, and Tomcat refuses one that climbs above it.
 *
 * @param goldSet the hand-labeled descriptions
 * @param reportDir where each run writes {@code {model}-{prompt version}.md}
 * @param summary the table of every run's headline numbers, beside the PSC baseline's
 * @param capUsd the most one run may spend; a request that could pass it isn't sent
 */
@ConfigurationProperties("awardtrace.enrichment.eval")
record EvalProperties(String goldSet, String reportDir, String summary, BigDecimal capUsd) {

    Path goldSetFile() {
        return Path.of(goldSet);
    }

    Path reportDirectory() {
        return Path.of(reportDir);
    }

    Path summaryFile() {
        return Path.of(summary);
    }
}
