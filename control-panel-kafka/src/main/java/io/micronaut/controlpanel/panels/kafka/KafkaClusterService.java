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
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsOptions;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberAssignment;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupMember;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupPartition;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupSummary;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessageHeader;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessagePage;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessageRecord;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Overview;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.PartitionDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.RenderedPayload;
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
    private static final Duration CONSUMER_POLL_TIMEOUT = Duration.ofMillis(250);
    private static final Duration CONSUMER_API_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_PAGE_LENGTH = 25;
    private static final int MAX_PAGE_LENGTH = 100;
    private static final int DEFAULT_RECORD_LIMIT = 25;
    private static final int MAX_RECORD_LIMIT = 100;
    private static final int MAX_PAYLOAD_DISPLAY_BYTES = 8 * 1024;
    private static final int MAX_POLLS = 8;
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
    private final KafkaMessageBrowserConsumerFactory consumerFactory;
    private final JsonMapper jsonMapper;

    @Inject
    KafkaClusterService(AdminClient adminClient,
                        KafkaMessageBrowserConsumerFactory consumerFactory,
                        JsonMapper jsonMapper) {
        this.adminClient = adminClient;
        this.consumerFactory = consumerFactory;
        this.jsonMapper = jsonMapper;
    }

    KafkaClusterService(AdminClient adminClient) {
        this(adminClient, null, JsonMapper.createDefault());
    }

    KafkaClusterService(AdminClient adminClient,
                        KafkaMessageBrowserConsumerFactory consumerFactory) {
        this(adminClient, consumerFactory, JsonMapper.createDefault());
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
            int safeLength = length <= 0 ? DEFAULT_PAGE_LENGTH : Math.min(length, MAX_PAGE_LENGTH);
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

    Section<List<ConsumerGroupSummary>> consumerGroups() {
        return section(() -> {
            List<String> groupIds = consumerGroupIds();
            if (groupIds.isEmpty()) {
                return List.of();
            }
            Map<String, ConsumerGroupDescription> descriptions = describeConsumerGroups(groupIds);
            Map<String, Map<TopicPartition, OffsetAndMetadata>> committedOffsets = consumerGroupOffsets(groupIds);
            java.util.ArrayList<ConsumerGroupSummary> summaries = new java.util.ArrayList<>(groupIds.size());
            for (String groupId : groupIds) {
                ConsumerGroupDescription description = descriptions.get(groupId);
                if (description != null) {
                    summaries.add(toConsumerGroupDetail(
                        description,
                        committedOffsets.getOrDefault(groupId, Map.of())
                    ).group());
                }
            }
            return summaries;
        });
    }

    Section<ConsumerGroupDetail> consumerGroup(String groupId) {
        return section(() -> {
            ConsumerGroupDescription description = describeConsumerGroups(List.of(groupId)).get(groupId);
            if (description == null) {
                throw new IllegalArgumentException("Consumer group not found or not authorized: " + groupId);
            }
            Map<TopicPartition, OffsetAndMetadata> offsets = consumerGroupOffsets(List.of(groupId))
                .getOrDefault(groupId, Map.of());
            return toConsumerGroupDetail(description, offsets);
        });
    }

    Section<MessagePage> messages(String topic,
                                  int partition,
                                  String mode,
                                  @Nullable Long offset,
                                  @Nullable Long timestamp,
                                  int limit) {
        return section(() -> {
            if (consumerFactory == null) {
                throw new IllegalStateException("Kafka message browser consumer factory is unavailable");
            }
            if (topic.isBlank()) {
                throw new IllegalArgumentException("Topic is required");
            }
            if (partition < 0) {
                throw new IllegalArgumentException("Partition must be greater than or equal to 0");
            }
            String safeMode = normalizeMessageMode(mode);
            int safeLimit = safeRecordLimit(limit);
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            try (Consumer<byte[], byte[]> consumer = consumerFactory.createConsumer()) {
                consumer.assign(List.of(topicPartition));
                BrowseOffsets browseOffsets = seekForBrowse(consumer, topicPartition, safeMode, offset, timestamp, safeLimit);
                List<MessageRecord> records = readRecords(consumer, topicPartition, safeLimit, browseOffsets.endOffset());
                return new MessagePage(
                    topic,
                    partition,
                    safeMode,
                    safeLimit,
                    browseOffsets.startOffset(),
                    browseOffsets.endOffset(),
                    records
                );
            }
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

    private List<String> consumerGroupIds() throws Exception {
        Collection<GroupListing> groups = await(adminClient
            .listGroups(new ListGroupsOptions())
            .all());
        return groups
            .stream()
            .map(GroupListing::groupId)
            .sorted()
            .toList();
    }

    private Map<String, ConsumerGroupDescription> describeConsumerGroups(Collection<String> groupIds) throws Exception {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return await(adminClient
            .describeConsumerGroups(groupIds, new DescribeConsumerGroupsOptions())
            .all());
    }

    private Map<String, Map<TopicPartition, OffsetAndMetadata>> consumerGroupOffsets(
        Collection<String> groupIds) throws Exception {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ListConsumerGroupOffsetsSpec> specs = groupIds.stream()
            .sorted()
            .collect(Collectors.toMap(
                groupId -> groupId,
                groupId -> new ListConsumerGroupOffsetsSpec(),
                (first, second) -> first,
                LinkedHashMap::new
            ));
        return await(adminClient
            .listConsumerGroupOffsets(specs, new ListConsumerGroupOffsetsOptions())
            .all());
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
                if (isUnderReplicatedBrokerPartition(partition, node.id())) {
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

    private static boolean isUnderReplicatedBrokerPartition(TopicPartitionInfo partition, int brokerId) {
        return containsBroker(partition.replicas(), brokerId)
            && partition.isr().size() < partition.replicas().size();
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

    private ConsumerGroupDetail toConsumerGroupDetail(
        ConsumerGroupDescription description,
        Map<TopicPartition, OffsetAndMetadata> committedOffsets) throws Exception {
        List<TopicPartition> assignedPartitions = description.members().stream()
            .flatMap(member -> member.assignment().topicPartitions().stream())
            .sorted(KafkaClusterService::compareTopicPartitions)
            .toList();
        List<TopicPartition> partitions = java.util.stream.Stream
            .concat(assignedPartitions.stream(), committedOffsets.keySet().stream())
            .distinct()
            .sorted(KafkaClusterService::compareTopicPartitions)
            .toList();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
            partitions.isEmpty() ? Map.of() : listOffsets(partitions, OffsetSpec.latest());
        List<ConsumerGroupPartition> groupPartitions = partitions.stream()
            .map(partition -> toConsumerGroupPartition(
                partition,
                assignedPartitions.contains(partition),
                committedOffsets.get(partition),
                endOffsets.get(partition)))
            .toList();
        Long totalLag = groupPartitions.stream()
            .map(ConsumerGroupPartition::lag)
            .reduce(0L, (total, lag) -> lag == null ? total : total + lag);
        ConsumerGroupSummary summary = new ConsumerGroupSummary(
            description.groupId(),
            description.groupState().toString(),
            description.partitionAssignor(),
            description.members().size(),
            assignedPartitions.size(),
            committedOffsets.size(),
            partitions.isEmpty() ? null : totalLag
        );
        return new ConsumerGroupDetail(
            summary,
            description.members().stream()
                .sorted(Comparator.comparing(MemberDescription::consumerId))
                .map(KafkaClusterService::toConsumerGroupMember)
                .toList(),
            groupPartitions
        );
    }

    private static ConsumerGroupMember toConsumerGroupMember(MemberDescription member) {
        return new ConsumerGroupMember(
            member.consumerId(),
            member.groupInstanceId().orElse(null),
            member.clientId(),
            member.host(),
            topicPartitionLabels(member.assignment())
        );
    }

    private static List<String> topicPartitionLabels(MemberAssignment assignment) {
        return assignment.topicPartitions().stream()
            .sorted(KafkaClusterService::compareTopicPartitions)
            .map(KafkaClusterService::topicPartitionLabel)
            .toList();
    }

    private static ConsumerGroupPartition toConsumerGroupPartition(
        TopicPartition partition,
        boolean assigned,
        @Nullable OffsetAndMetadata committedOffset,
        ListOffsetsResult.ListOffsetsResultInfo endOffset) {
        Long committed = committedOffset == null ? null : committedOffset.offset();
        Long end = offset(endOffset);
        Long lag = committed == null || end == null ? null : Math.max(0, end - committed);
        return new ConsumerGroupPartition(
            partition.topic(),
            partition.partition(),
            assigned,
            committed,
            end,
            lag
        );
    }

    private BrowseOffsets seekForBrowse(Consumer<byte[], byte[]> consumer,
                                        TopicPartition partition,
                                        String mode,
                                        @Nullable Long offset,
                                        @Nullable Long timestamp,
                                        int limit) {
        Long beginningOffset = null;
        Long endOffset = null;
        Long startOffset;
        switch (mode) {
            case "beginning" -> {
                consumer.seekToBeginning(List.of(partition));
                startOffset = consumer.position(partition, CONSUMER_API_TIMEOUT);
            }
            case "latest" -> {
                Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(List.of(partition), CONSUMER_API_TIMEOUT);
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(List.of(partition), CONSUMER_API_TIMEOUT);
                beginningOffset = beginningOffsets.get(partition);
                endOffset = endOffsets.get(partition);
                if (endOffset == null) {
                    consumer.seekToEnd(List.of(partition));
                    startOffset = consumer.position(partition, CONSUMER_API_TIMEOUT);
                } else {
                    startOffset = Math.max(beginningOffset == null ? 0L : beginningOffset, endOffset - limit);
                    consumer.seek(partition, startOffset);
                }
            }
            case "offset" -> {
                if (offset == null || offset < 0) {
                    throw new IllegalArgumentException("Offset must be greater than or equal to 0");
                }
                startOffset = offset;
                consumer.seek(partition, offset);
            }
            case "timestamp" -> {
                if (timestamp == null || timestamp < 0) {
                    throw new IllegalArgumentException("Timestamp must be greater than or equal to 0");
                }
                Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(
                    Map.of(partition, timestamp),
                    CONSUMER_API_TIMEOUT
                );
                OffsetAndTimestamp offsetAndTimestamp = offsets.get(partition);
                if (offsetAndTimestamp == null) {
                    consumer.seekToEnd(List.of(partition));
                    startOffset = consumer.position(partition, CONSUMER_API_TIMEOUT);
                } else {
                    startOffset = offsetAndTimestamp.offset();
                    consumer.seek(partition, startOffset);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported message browse mode: " + mode);
        }
        return new BrowseOffsets(startOffset, endOffset);
    }

    private List<MessageRecord> readRecords(Consumer<byte[], byte[]> consumer,
                                            TopicPartition partition,
                                            int limit,
                                            @Nullable Long endOffset) {
        List<MessageRecord> records = new ArrayList<>(limit);
        for (int i = 0; i < MAX_POLLS && records.size() < limit; i++) {
            ConsumerRecords<byte[], byte[]> consumerRecords = consumer.poll(CONSUMER_POLL_TIMEOUT);
            for (ConsumerRecord<byte[], byte[]> record : consumerRecords.records(partition)) {
                if (endOffset != null && record.offset() >= endOffset) {
                    return records;
                }
                records.add(toMessageRecord(record));
                if (records.size() >= limit) {
                    break;
                }
            }
            if (consumerRecords.isEmpty()) {
                break;
            }
        }
        return records;
    }

    private MessageRecord toMessageRecord(ConsumerRecord<byte[], byte[]> record) {
        List<MessageHeader> headers = new ArrayList<>();
        for (Header header : record.headers()) {
            headers.add(new MessageHeader(header.key(), renderPayload(header.value())));
        }
        return new MessageRecord(
            record.offset(),
            record.timestamp(),
            record.timestampType().toString(),
            renderPayload(record.key()),
            renderPayload(record.value()),
            headers,
            record.partition()
        );
    }

    @Nullable
    private RenderedPayload renderPayload(byte @Nullable [] bytes) {
        if (bytes == null) {
            return null;
        }
        boolean truncated = bytes.length > MAX_PAYLOAD_DISPLAY_BYTES;
        byte[] displayBytes = truncated ? Arrays.copyOf(bytes, MAX_PAYLOAD_DISPLAY_BYTES) : bytes;
        String text = decodeUtf8(displayBytes, truncated);
        if (text != null) {
            String trimmed = text.trim();
            if (isJsonCandidate(trimmed)) {
                String prettyJson = prettyJson(displayBytes);
                if (prettyJson != null) {
                    return new RenderedPayload("json", prettyJson, null, bytes.length, truncated);
                }
            }
            return new RenderedPayload("utf8", text, null, bytes.length, truncated);
        }
        return new RenderedPayload(
            "base64",
            null,
            Base64.getEncoder().encodeToString(displayBytes),
            bytes.length,
            truncated
        );
    }

    @Nullable
    private static String decodeUtf8(byte[] bytes, boolean allowTrim) {
        byte[] candidate = bytes;
        for (int i = 0; i < 4; i++) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(candidate))
                    .toString();
            } catch (CharacterCodingException e) {
                if (!allowTrim || candidate.length == 0) {
                    return null;
                }
                candidate = Arrays.copyOf(candidate, candidate.length - 1);
            }
        }
        return null;
    }

    private static boolean isJsonCandidate(String text) {
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
    }

    @Nullable
    private String prettyJson(byte[] bytes) {
        try {
            JsonNode node = jsonMapper.readValue(bytes, JsonNode.class);
            StringBuilder builder = new StringBuilder();
            appendJson(builder, node, 0);
            return builder.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static void appendJson(StringBuilder builder, JsonNode node, int indent) {
        if (node.isObject()) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> entry : node.entries()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(',');
                }
                builder.append('\n');
                appendIndent(builder, indent + 2);
                appendQuoted(builder, entry.getKey());
                builder.append(": ");
                appendJson(builder, entry.getValue(), indent + 2);
            }
            if (!first) {
                builder.append('\n');
                appendIndent(builder, indent);
            }
            builder.append('}');
        } else if (node.isArray()) {
            builder.append('[');
            boolean first = true;
            for (JsonNode value : node.values()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(',');
                }
                builder.append('\n');
                appendIndent(builder, indent + 2);
                appendJson(builder, value, indent + 2);
            }
            if (!first) {
                builder.append('\n');
                appendIndent(builder, indent);
            }
            builder.append(']');
        } else if (node.isString()) {
            appendQuoted(builder, node.getStringValue());
        } else if (node.isNumber() || node.isBoolean()) {
            builder.append(node.getValue());
        } else {
            builder.append("null");
        }
    }

    private static void appendIndent(StringBuilder builder, int indent) {
        builder.append(" ".repeat(indent));
    }

    private static void appendQuoted(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append("\\u%04x".formatted((int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static String normalizeMessageMode(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "beginning", "latest", "offset", "timestamp" -> mode.toLowerCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("Unsupported message browse mode: " + mode);
        };
    }

    private static int safeRecordLimit(int limit) {
        return limit <= 0 ? DEFAULT_RECORD_LIMIT : Math.min(limit, MAX_RECORD_LIMIT);
    }

    private Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> listOffsets(
        Collection<TopicPartition> partitions,
        OffsetSpec offsetSpec) throws Exception {
        Map<TopicPartition, OffsetSpec> requests = partitions.stream()
            .collect(Collectors.toMap(
                partition -> partition,
                partition -> offsetSpec,
                (first, second) -> first,
                LinkedHashMap::new
            ));
        if (requests.isEmpty()) {
            return Map.of();
        }
        return await(adminClient.listOffsets(requests, new ListOffsetsOptions()).all());
    }

    private static int compareTopicPartitions(TopicPartition first, TopicPartition second) {
        int topic = first.topic().compareTo(second.topic());
        return topic == 0 ? Integer.compare(first.partition(), second.partition()) : topic;
    }

    private static String topicPartitionLabel(TopicPartition partition) {
        return partition.topic() + "-" + partition.partition();
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

    private record BrowseOffsets(@Nullable Long startOffset, @Nullable Long endOffset) {
    }
}
