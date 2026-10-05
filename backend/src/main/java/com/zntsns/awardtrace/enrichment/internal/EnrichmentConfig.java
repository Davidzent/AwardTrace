package com.zntsns.awardtrace.enrichment.internal;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("enricher")
@EnableConfigurationProperties(EnrichmentProperties.class)
class EnrichmentConfig {

    /**
     * The SDK reads the key from {@code ANTHROPIC_API_KEY} itself, so it never passes through Spring's configuration,
     * where an endpoint or a log could show it. Without a key the client still starts, and each request fails
     * authentication instead: enrichment only adds, so it must not stop the roles it shares a process with (doc 09).
     * The SDK retries rate limits, server errors, and dropped connections twice, with backoff; a 25-item request takes
     * seconds, so two minutes means a hung connection, not a slow answer.
     */
    @Bean
    AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder().fromEnv().timeout(Duration.ofMinutes(2)).build();
    }
}
