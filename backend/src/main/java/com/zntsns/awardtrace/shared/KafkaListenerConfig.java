package com.zntsns.awardtrace.shared;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaListenerConfig {

    /**
     * Every listener handles bad events itself, so whatever reaches this handler is an infrastructure failure: a
     * database, the broker, or Elasticsearch is down. The whole batch is retried, backing off to once a minute, for
     * as long as it takes; nothing is skipped and no offset moves until the batch succeeds.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        var backOff = new ExponentialBackOff(Duration.ofSeconds(1).toMillis(), 2);
        backOff.setMaxInterval(Duration.ofMinutes(1).toMillis());
        return new DefaultErrorHandler(backOff);
    }
}
