package com.zntsns.awardtrace.search.internal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Reads topic and consumer group offsets through the broker's admin API. Every call gives up after five seconds, so
 * an unreachable broker slows the status page down but never hangs it.
 */
@Component
@Profile("api")
class KafkaOffsets implements DisposableBean {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Admin admin;

    KafkaOffsets(KafkaAdmin kafkaAdmin) {
        this.admin = Admin.create(kafkaAdmin.getConfigurationProperties());
    }

    /**
     * Events a consumer group has yet to read from a topic. On a partition where the group has committed nothing,
     * or less than retention has since deleted, it would start at the earliest retained event, so it counts from
     * there.
     */
    long lag(String group, String topic) throws ExecutionException, TimeoutException, InterruptedException {
        var partitions = partitions(topic);
        var committed = get(admin.listConsumerGroupOffsets(group, new ListConsumerGroupOffsetsOptions()
                .timeoutMs((int) TIMEOUT.toMillis())).partitionsToOffsetAndMetadata());
        var earliest = offsets(partitions, OffsetSpec.earliest());
        var latest = offsets(partitions, OffsetSpec.latest());
        long lag = 0;
        for (var partition : partitions) {
            var offset = committed.get(partition);
            long position = Math.max(offset == null ? 0 : offset.offset(), earliest.get(partition));
            lag += latest.get(partition) - position;
        }
        return lag;
    }

    /** Events a topic still holds within its retention. */
    long retained(String topic) throws ExecutionException, TimeoutException, InterruptedException {
        var partitions = partitions(topic);
        var earliest = offsets(partitions, OffsetSpec.earliest());
        var latest = offsets(partitions, OffsetSpec.latest());
        return partitions.stream().mapToLong(partition -> latest.get(partition) - earliest.get(partition)).sum();
    }

    private List<TopicPartition> partitions(String topic)
            throws ExecutionException, TimeoutException, InterruptedException {
        var description = get(admin.describeTopics(List.of(topic), new DescribeTopicsOptions()
                .timeoutMs((int) TIMEOUT.toMillis())).allTopicNames()).get(topic);
        return description.partitions().stream()
                .map(partition -> new TopicPartition(topic, partition.partition()))
                .toList();
    }

    private Map<TopicPartition, Long> offsets(List<TopicPartition> partitions, OffsetSpec spec)
            throws ExecutionException, TimeoutException, InterruptedException {
        var request = partitions.stream().collect(Collectors.toMap(partition -> partition, partition -> spec));
        var result = get(admin.listOffsets(request, new ListOffsetsOptions().timeoutMs((int) TIMEOUT.toMillis()))
                .all());
        return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()
                .offset()));
    }

    private static <T> T get(KafkaFuture<T> future)
            throws ExecutionException, TimeoutException, InterruptedException {
        return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        admin.close(TIMEOUT);
    }
}
