package com.zntsns.awardtrace.shared;

import static com.zntsns.awardtrace.shared.KafkaTopics.AWARDS_CHANGED;
import static com.zntsns.awardtrace.shared.KafkaTopics.AWARD_TRANSACTIONS;
import static com.zntsns.awardtrace.shared.KafkaTopics.AWARD_TRANSACTIONS_DLT;
import static com.zntsns.awardtrace.shared.KafkaTopics.SUBAWARDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.zntsns.awardtrace.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class KafkaTopicsIT {

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Test
    void createsEveryTopicWithItsPartitionsAndRetention() throws Exception {
        var topics = List.of(AWARD_TRANSACTIONS, AWARD_TRANSACTIONS_DLT, AWARDS_CHANGED, SUBAWARDS);

        assertThat(kafkaAdmin.describeTopics(topics.toArray(String[]::new)).values())
                .hasSize(topics.size())
                .allSatisfy(topic -> assertThat(topic.partitions()).hasSize(KafkaTopics.PARTITIONS));

        Map<String, Config> configs = describeConfigs(topics);
        assertThat(value(configs, AWARD_TRANSACTIONS, TopicConfig.CLEANUP_POLICY_CONFIG)).isEqualTo("delete");
        assertThat(value(configs, AWARD_TRANSACTIONS, TopicConfig.RETENTION_MS_CONFIG)).isEqualTo("604800000");
        assertThat(value(configs, AWARD_TRANSACTIONS_DLT, TopicConfig.RETENTION_MS_CONFIG)).isEqualTo("2592000000");
        assertThat(value(configs, AWARDS_CHANGED, TopicConfig.CLEANUP_POLICY_CONFIG)).isEqualTo("compact");
        assertThat(value(configs, SUBAWARDS, TopicConfig.RETENTION_MS_CONFIG)).isEqualTo("604800000");
    }

    private Map<String, Config> describeConfigs(List<String> topics) throws Exception {
        var resources = topics.stream().map(topic -> new ConfigResource(ConfigResource.Type.TOPIC, topic)).toList();
        try (var client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            return client.describeConfigs(resources).all().get().entrySet().stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        }
    }

    private static String value(Map<String, Config> configs, String topic, String key) {
        return configs.get(topic).get(key).value();
    }
}
