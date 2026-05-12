/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.kafka;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Broker;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.BrokerNode;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Overview;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.PartitionDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Section;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicPage;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicSummary;

/**
 * Read-only facade over Kafka AdminClient APIs for the cluster panel.
 */
@Singleton
@Internal
@Requires(beans = AdminClient.class)
@Requires(property = KafkaClusterControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
final class KafkaClusterService {

    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_PAGE_LENGTH = 25;
    private static final List<String> SECRET_NAME_PARTS = List.of(
        "password",
        "secret",
        "token",
        "credential",
        "jaas",
        "sasl",
        "keystore",
        "truststore",
        "private.key",
        "ssl.key"
    );

    private final AdminClient adminClient;

    KafkaClusterService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    Section<Overview> overview() {
        return section(() -> {
            var cluster = adminClient.describeCluster(new DescribeClusterOptions());
            Collection<Node> nodes = await(cluster.nodes());
            Node controller = await(cluster.controller());
            String clusterId = await(cluster.clusterId());
            Map<String, TopicDescription> topics = describeAllTopics();
            int partitionCount = topics.values().stream().mapToInt(topic -> topic.partitions().size()).sum();
            int consumerGroupCount = await(adminClient.listGroups(new ListGroupsOptions()).all()).size();
            Map<String, String> config = firstBrokerConfig(nodes);
            return new Overview(
                clusterId,
                toBrokerNode(controller),
                nodes.size(),
                topics.size(),
                partitionCount,
                consumerGroupCount,
                config
            );
        });
    }

    Section<List<Broker>> brokers() {
        return section(() -> {
            var cluster = adminClient.describeCluster(new DescribeClusterOptions());
            Collection<Node> nodes = await(cluster.nodes());
            Node controller = await(cluster.controller());
            Map<String, TopicDescription> topics = describeAllTopics();
            Map<Integer, Map<String, String>> configs = brokerConfigs(nodes);
            return nodes.stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(node -> toBroker(node, controller, topics.values(), configs.getOrDefault(node.id(), Map.of())))
                .toList();
        });
    }

    Section<TopicPage> topics(@Nullable String search, boolean includeInternal, int start, int length) {
        return section(() -> {
            int safeStart = Math.max(start, 0);
            int safeLength = length <= 0 ? DEFAULT_PAGE_LENGTH : length;
            String normalizedSearch = search == null ? "" : search.toLowerCase(Locale.ROOT);
            Map<String, TopicDescription> descriptions = describeAllTopics();
            List<TopicDescription> allTopics = descriptions.values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .toList();
            List<TopicDescription> filtered = allTopics.stream()
                .filter(topic -> includeInternal || !topic.isInternal())
                .filter(topic -> normalizedSearch.isBlank()
                    || topic.name().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .toList();
            List<TopicDescription> pageDescriptions = filtered.stream()
                .skip(safeStart)
                .limit(safeLength)
                .toList();
            Map<String, Map<String, String>> configs = topicConfigs(pageDescriptions.stream()
                .map(TopicDescription::name)
                .toList());
            List<TopicSummary> page = pageDescriptions.stream()
                .map(topic -> toTopicSummary(topic, configs.getOrDefault(topic.name(), Map.of())))
                .toList();
            return new TopicPage(safeStart, safeLength, allTopics.size(), filtered.size(), page);
        });
    }

    Section<TopicDetail> topic(String topicName) {
        return section(() -> {
            TopicDescription description = await(adminClient
                .describeTopics(TopicCollection.ofTopicNames(List.of(topicName)), new DescribeTopicsOptions())
                .allTopicNames()).get(topicName);
            if (description == null) {
                throw new IllegalArgumentException("Topic not found or not authorized: " + topicName);
            }
            Map<String, String> config = topicConfigs(List.of(topicName)).getOrDefault(topicName, Map.of());
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> beginningOffsets =
                listOffsets(description, OffsetSpec.earliest());
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                listOffsets(description, OffsetSpec.latest());
            List<PartitionDetail> partitions = description.partitions().stream()
                .sorted(Comparator.comparingInt(TopicPartitionInfo::partition))
                .map(partition -> toPartitionDetail(description.name(), partition, beginningOffsets, endOffsets))
                .toList();
            return new TopicDetail(toTopicSummary(description, config), partitions);
        });
    }

    static boolean isSafeConfig(ConfigEntry entry) {
        if (entry.isSensitive()) {
            return false;
        }
        String name = entry.name().toLowerCase(Locale.ROOT);
        return SECRET_NAME_PARTS.stream().noneMatch(name::contains);
    }

    private <T> Section<T> section(Callable<T> callable) {
        try {
            return Section.ok(callable.call());
        } catch (Exception e) {
            return Section.error(errorMessage(e));
        }
    }

    private static String errorMessage(Exception e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    private Map<String, TopicDescription> describeAllTopics() throws Exception {
        Collection<TopicListing> listings = await(adminClient
            .listTopics(new ListTopicsOptions().listInternal(true))
            .listings());
        List<String> topicNames = listings.stream()
            .map(TopicListing::name)
            .sorted()
            .toList();
        if (topicNames.isEmpty()) {
            return Map.of();
        }
        return await(adminClient
            .describeTopics(TopicCollection.ofTopicNames(topicNames), new DescribeTopicsOptions())
            .allTopicNames());
    }

    private Map<Integer, Map<String, String>> brokerConfigs(Collection<Node> nodes) throws Exception {
        List<ConfigResource> resources = nodes.stream()
            .sorted(Comparator.comparingInt(Node::id))
            .map(node -> new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(node.id())))
            .toList();
        if (resources.isEmpty()) {
            return Map.of();
        }
        Map<ConfigResource, Config> configs = await(adminClient
            .describeConfigs(resources, new DescribeConfigsOptions())
            .all());
        Map<Integer, Map<String, String>> result = new LinkedHashMap<>();
        for (ConfigResource resource : resources) {
            Config config = configs.get(resource);
            if (config != null) {
                result.put(Integer.valueOf(resource.name()), filteredConfig(config));
            }
        }
        return result;
    }

    private Map<String, Map<String, String>> topicConfigs(Collection<String> topicNames) throws Exception {
        List<ConfigResource> resources = topicNames.stream()
            .sorted()
            .map(topic -> new ConfigResource(ConfigResource.Type.TOPIC, topic))
            .toList();
        if (resources.isEmpty()) {
            return Map.of();
        }
        Map<ConfigResource, Config> configs = await(adminClient
            .describeConfigs(resources, new DescribeConfigsOptions())
            .all());
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (ConfigResource resource : resources) {
            Config config = configs.get(resource);
            if (config != null) {
                result.put(resource.name(), filteredConfig(config));
            }
        }
        return result;
    }

    private Map<String, String> firstBrokerConfig(Collection<Node> nodes) throws Exception {
        return brokerConfigs(nodes).values().stream().findFirst().orElse(Map.of());
    }

    private static Map<String, String> filteredConfig(Config config) {
        return config.entries().stream()
            .filter(KafkaClusterService::isSafeConfig)
            .sorted(Comparator.comparing(ConfigEntry::name))
            .collect(Collectors.toMap(
                ConfigEntry::name,
                entry -> Optional.ofNullable(entry.value()).orElse(""),
                (first, second) -> first,
                LinkedHashMap::new
            ));
    }

    private Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> listOffsets(
        TopicDescription topic,
        OffsetSpec offsetSpec) throws Exception {
        Map<TopicPartition, OffsetSpec> requests = topic.partitions().stream()
            .collect(Collectors.toMap(
                partition -> new TopicPartition(topic.name(), partition.partition()),
                partition -> offsetSpec,
                (first, second) -> first,
                LinkedHashMap::new
            ));
        if (requests.isEmpty()) {
            return Map.of();
        }
        return await(adminClient.listOffsets(requests, new ListOffsetsOptions()).all());
    }

    private Broker toBroker(Node node,
                            Node controller,
                            Collection<TopicDescription> topics,
                            Map<String, String> config) {
        int leaderPartitions = 0;
        int replicaPartitions = 0;
        int underReplicatedPartitions = 0;
        for (TopicDescription topic : topics) {
            for (TopicPartitionInfo partition : topic.partitions()) {
                if (partition.leader() != null && partition.leader().id() == node.id()) {
                    leaderPartitions++;
                }
                if (containsBroker(partition.replicas(), node.id())) {
                    replicaPartitions++;
                }
                if (containsBroker(partition.replicas(), node.id())
                    && partition.isr().size() < partition.replicas().size()) {
                    underReplicatedPartitions++;
                }
            }
        }
        return new Broker(
            node.id(),
            node.host(),
            node.port(),
            node.hasRack() ? node.rack() : null,
            controller != null && controller.id() == node.id(),
            leaderPartitions,
            replicaPartitions,
            underReplicatedPartitions,
            config
        );
    }

    private static boolean containsBroker(Collection<Node> nodes, int brokerId) {
        return nodes.stream().anyMatch(node -> node.id() == brokerId);
    }

    private static TopicSummary toTopicSummary(TopicDescription topic, Map<String, String> config) {
        int replicationFactor = topic.partitions().stream()
            .findFirst()
            .map(partition -> partition.replicas().size())
            .orElse(0);
        boolean underReplicated = topic.partitions().stream()
            .anyMatch(partition -> partition.isr().size() < partition.replicas().size());
        return new TopicSummary(
            topic.name(),
            topic.isInternal(),
            topic.partitions().size(),
            replicationFactor,
            underReplicated,
            config.get("cleanup.policy"),
            config.get("retention.ms"),
            config.get("retention.bytes"),
            config.get("segment.bytes"),
            config
        );
    }

    private static PartitionDetail toPartitionDetail(
        String topicName,
        TopicPartitionInfo partition,
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> beginningOffsets,
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets) {
        TopicPartition topicPartition = new TopicPartition(topicName, partition.partition());
        Long beginning = offset(beginningOffsets.get(topicPartition));
        Long end = offset(endOffsets.get(topicPartition));
        Long estimate = beginning == null || end == null ? null : Math.max(0, end - beginning);
        return new PartitionDetail(
            partition.partition(),
            toBrokerNode(partition.leader()),
            partition.replicas().stream().map(KafkaClusterService::toBrokerNode).toList(),
            partition.isr().stream().map(KafkaClusterService::toBrokerNode).toList(),
            beginning,
            end,
            estimate
        );
    }

    @Nullable
    private static Long offset(ListOffsetsResult.ListOffsetsResultInfo info) {
        return info == null ? null : info.offset();
    }

    @Nullable
    private static BrokerNode toBrokerNode(@Nullable Node node) {
        if (node == null || node.isEmpty()) {
            return null;
        }
        return new BrokerNode(node.id(), node.host(), node.port(), node.hasRack() ? node.rack() : null);
    }

    private static <T> T await(KafkaFuture<T> future) throws Exception {
        return future.get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }
}
