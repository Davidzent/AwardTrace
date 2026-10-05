package com.zntsns.awardtrace.enrichment.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param model the Claude model that classifies, such as {@code claude-haiku-4-5}
 * @param promptVersion the system prompt, {@code prompts/classify-{promptVersion}.txt}, such as {@code v1}
 */
@ConfigurationProperties("awardtrace.enrichment")
record EnrichmentProperties(String model, String promptVersion) {
}
