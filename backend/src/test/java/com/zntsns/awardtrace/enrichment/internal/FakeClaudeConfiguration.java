package com.zntsns.awardtrace.enrichment.internal;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Points the enricher's Claude client at {@link FakeClaude}, so no test calls the Claude API (doc 09). Every enricher
 * integration test imports it, so they share one context and one set of containers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeClaudeConfiguration {

    @Bean(destroyMethod = "stop")
    FakeClaude fakeClaude() {
        return new FakeClaude();
    }

    @Bean
    @Primary
    AnthropicClient fakeClaudeClient(FakeClaude claude) {
        return AnthropicOkHttpClient.builder().baseUrl(claude.url()).apiKey("test-key").maxRetries(0).build();
    }
}
