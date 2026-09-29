package com.zntsns.awardtrace;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Elasticsearch for the tests that need it, imported alongside {@link TestcontainersConfiguration}. It is kept
 * separate because each test context runs its own containers, and most contexts never touch search.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestConfiguration {

    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearch() {
        // Security off, as in infra/compose/compose.local.yml.
        return new ElasticsearchContainer("elasticsearch:9.5.3")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }
}
