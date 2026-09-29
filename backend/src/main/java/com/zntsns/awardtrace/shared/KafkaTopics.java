package com.zntsns.awardtrace.shared;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic names, and the topic definitions that {@code KafkaAdmin} creates at startup. The broker never
 * auto-creates topics, so every topic the application uses is declared here.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopics {

    public static final String AWARD_TRANSACTIONS = "awards.transactions.v1";
    public static final String AWARD_TRANSACTIONS_DLT = AWARD_TRANSACTIONS + ".DLT";
    public static final String AWARDS_CHANGED = "awards.changed.v1";

    // Fixed once chosen: changing it remaps award IDs to partitions. Dead-letter topics need the same
    // count, because the dead-letter recoverer writes to the record's original partition number.
    // Replication is left to the broker's default so a larger cluster needs no code change.
    static final int PARTITIONS = 6;

    @Bean
    NewTopic awardTransactionsTopic() {
        return deleteAfter(AWARD_TRANSACTIONS, Duration.ofDays(7));
    }

    @Bean
    NewTopic awardTransactionsDltTopic() {
        return deleteAfter(AWARD_TRANSACTIONS_DLT, Duration.ofDays(30));
    }

    @Bean
    NewTopic awardsChangedTopic() {
        return TopicBuilder.name(AWARDS_CHANGED).partitions(PARTITIONS).compact().build();
    }

    private static NewTopic deleteAfter(String name, Duration retention) {
        return TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retention.toMillis()))
                .build();
    }
}
