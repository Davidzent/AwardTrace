package com.zntsns.awardtrace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The same engines and versions as infra/compose, shared by every integration test through context caching. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String RAW_BUCKET = "awardtrace-raw";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    @Bean
    GenericContainer<?> s3mock() {
        return new GenericContainer<>("adobe/s3mock:5.2.3")
                .withExposedPorts(9090)
                .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", RAW_BUCKET)
                .waitingFor(Wait.forHttp("/favicon.ico"));
    }

    @Bean
    DynamicPropertyRegistrar s3mockProperties(@Qualifier("s3mock") GenericContainer<?> s3mock) {
        return registry -> {
            registry.add("awardtrace.s3.bucket", () -> RAW_BUCKET);
            registry.add("awardtrace.s3.endpoint",
                    () -> "http://" + s3mock.getHost() + ":" + s3mock.getMappedPort(9090));
        };
    }
}
