package com.zntsns.awardtrace.enrichment.internal;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param model the Claude model that classifies, such as {@code claude-haiku-4-5}
 * @param promptVersion the system prompt, {@code prompts/classify-{promptVersion}.txt}, such as {@code v1}
 * @param dailyCapUsd the most the live path may spend on the Claude API in a UTC day
 */
@ConfigurationProperties("awardtrace.enrichment")
record EnrichmentProperties(String model, String promptVersion, BigDecimal dailyCapUsd) {
}
