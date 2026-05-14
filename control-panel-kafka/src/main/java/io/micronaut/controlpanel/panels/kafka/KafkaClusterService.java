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

import io.micronaut.configuration.kafka.ConsumerRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.AlterConfigsOptions;
import org.apache.kafka.clients.admin.AlterConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.CreatePartitionsOptions;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.DeleteConsumerGroupsOptions;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
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
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
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
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
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

import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.AppConsumer;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ActionResult;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.AppConsumerAssignment;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.AppConsumerActionRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Broker;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.BrokerNode;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupMember;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupPartition;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ConsumerGroupSummary;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.CreateTopicRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.DeleteConsumerGroupRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.DeleteTopicRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.IncreasePartitionsRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessageHeaderInput;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessageHeader;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessagePage;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.MessageRecord;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Overview;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.PartitionDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ProduceMessageRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.RenderedPayload;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.ResetOffsetsRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.Section;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicDetail;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicPage;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicPartitionInput;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.TopicSummary;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.UpdateTopicConfigRequest;
import static io.micronaut.controlpanel.panels.kafka.KafkaClusterResponse.WriteCapabilities;

/**
 * Facade over Kafka APIs for the cluster panel.
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
    private final @Nullable ConsumerRegistry consumerRegistry;
    private final @Nullable KafkaMessageBrowserConsumerFactory consumerFactory;
    private final @Nullable KafkaManagementProducerFactory producerFactory;
    private final JsonMapper jsonMapper;
    private final KafkaClusterWriteConfiguration writeConfiguration;

    @Inject
    KafkaClusterService(AdminClient adminClient,
                        @Nullable ConsumerRegistry consumerRegistry,
                        KafkaMessageBrowserConsumerFactory consumerFactory,
                        @Nullable KafkaManagementProducerFactory producerFactory,
                        JsonMapper jsonMapper,
                        KafkaClusterWriteConfiguration writeConfiguration) {
        this.adminClient = adminClient;
        this.consumerRegistry = consumerRegistry;
        this.consumerFactory = consumerFactory;
        this.producerFactory = producerFactory;
        this.jsonMapper = jsonMapper;
        this.writeConfiguration = writeConfiguration;
    }

    KafkaClusterService(AdminClient adminClient) {
        this(adminClient, null, null, null, JsonMapper.createDefault(), new KafkaClusterWriteConfiguration());
    }

    KafkaClusterService(AdminClient adminClient,
                        KafkaMessageBrowserConsumerFactory consumerFactory) {
        this(adminClient, null, consumerFactory, null, JsonMapper.createDefault(), new KafkaClusterWriteConfiguration());
    }

    KafkaClusterService(AdminClient adminClient,
                        ConsumerRegistry consumerRegistry) {
        this(adminClient, consumerRegistry, null, null, JsonMapper.createDefault(), new KafkaClusterWriteConfiguration());
    }

    KafkaClusterService(AdminClient adminClient,
                        @Nullable ConsumerRegistry consumerRegistry,
                        @Nullable KafkaMessageBrowserConsumerFactory consumerFactory,
                        @Nullable KafkaManagementProducerFactory producerFactory,
                        KafkaClusterWriteConfiguration writeConfiguration) {
        this(adminClient, consumerRegistry, consumerFactory, producerFactory, JsonMapper.createDefault(), writeConfiguration);
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

    Section<List<AppConsumer>> appConsumers() {
        return section(() -> {
            ConsumerRegistry registry = consumerRegistry;
            if (registry == null) {
                return List.of();
            }
            return registry.getConsumerIds()
                .stream()
                .sorted()
                .map(this::toAppConsumer)
                .toList();
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

    Section<WriteCapabilities> writeCapabilities() {
        return Section.ok(new WriteCapabilities(
            writeConfiguration.isEnabled(),
            writeConfiguration.isDestructiveEnabled(),
            Map.of(
                "topics.create", writeConfiguration.actionEnabled("topics.create"),
                "topics.update-config", writeConfiguration.actionEnabled("topics.update-config"),
                "topics.increase-partitions", writeConfiguration.actionEnabled("topics.increase-partitions"),
                "topics.delete", writeConfiguration.actionEnabled("topics.delete"),
                "messages.produce", writeConfiguration.actionEnabled("messages.produce"),
                "consumer-groups.delete", writeConfiguration.actionEnabled("consumer-groups.delete"),
                "consumer-groups.reset-offsets", writeConfiguration.actionEnabled("consumer-groups.reset-offsets"),
                "app-consumers.pause", writeConfiguration.actionEnabled("app-consumers.pause"),
                "app-consumers.resume", writeConfiguration.actionEnabled("app-consumers.resume")
            )
        ));
    }

    Section<ActionResult> createTopic(CreateTopicRequest request) {
        return section(() -> {
            String topic = requireName(request.topic(), "Topic");
            validatePositive(request.partitions(), "Partitions");
            if (request.replicationFactor() <= 0) {
                throw new IllegalArgumentException("Replication factor must be greater than 0");
            }
            String action = "topics.create";
            guardWrite(action, false);
            String impact = "Create topic " + topic + " with " + request.partitions()
                + " partitions and replication factor " + request.replicationFactor();
            if (request.preview()) {
                return previewResult(action, topic, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("CREATE", topic));
            NewTopic newTopic = new NewTopic(topic, request.partitions(), request.replicationFactor());
            if (request.config() != null && !request.config().isEmpty()) {
                newTopic.configs(new LinkedHashMap<>(request.config()));
            }
            await(adminClient.createTopics(List.of(newTopic), new CreateTopicsOptions()).all());
            return appliedResult(action, topic, impact);
        });
    }

    Section<ActionResult> updateTopicConfig(UpdateTopicConfigRequest request) {
        return section(() -> {
            String topic = requireName(request.topic(), "Topic");
            if (request.config() == null || request.config().isEmpty()) {
                throw new IllegalArgumentException("At least one config entry is required");
            }
            String action = "topics.update-config";
            guardWrite(action, false);
            String impact = "Update " + request.config().size() + " config entries for topic " + topic;
            if (request.preview()) {
                return previewResult(action, topic, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("UPDATE CONFIG", topic));
            List<AlterConfigOp> operations = request.config().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AlterConfigOp(
                    new ConfigEntry(entry.getKey(), entry.getValue()),
                    entry.getValue() == null ? AlterConfigOp.OpType.DELETE : AlterConfigOp.OpType.SET
                ))
                .toList();
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            await(adminClient.incrementalAlterConfigs(
                Map.of(resource, operations),
                new AlterConfigsOptions()
            ).all());
            return appliedResult(action, topic, impact);
        });
    }

    Section<ActionResult> increasePartitions(IncreasePartitionsRequest request) {
        return section(() -> {
            String topic = requireName(request.topic(), "Topic");
            validatePositive(request.totalPartitions(), "Total partitions");
            String action = "topics.increase-partitions";
            guardWrite(action, false);
            String impact = "Increase topic " + topic + " to " + request.totalPartitions() + " partitions";
            if (request.preview()) {
                return previewResult(action, topic, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("INCREASE PARTITIONS", topic));
            await(adminClient.createPartitions(
                Map.of(topic, NewPartitions.increaseTo(request.totalPartitions())),
                new CreatePartitionsOptions()
            ).all());
            return appliedResult(action, topic, impact);
        });
    }

    Section<ActionResult> deleteTopic(DeleteTopicRequest request) {
        return section(() -> {
            String topic = requireName(request.topic(), "Topic");
            String action = "topics.delete";
            guardWrite(action, true);
            String impact = "Delete topic " + topic;
            if (request.preview()) {
                return previewResult(action, topic, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("DELETE", topic));
            await(adminClient.deleteTopics(TopicCollection.ofTopicNames(List.of(topic)), new DeleteTopicsOptions()).all());
            return appliedResult(action, topic, impact);
        });
    }

    Section<ActionResult> produceMessage(ProduceMessageRequest request) {
        return section(() -> {
            String topic = requireName(request.topic(), "Topic");
            String action = "messages.produce";
            guardWrite(action, false);
            byte[] value = payloadBytes(request.value(), request.format(), "Value");
            byte @Nullable [] key = request.key() == null ? null : request.key().getBytes(StandardCharsets.UTF_8);
            Integer partition = request.partition();
            if (partition != null && partition < 0) {
                throw new IllegalArgumentException("Partition must be greater than or equal to 0");
            }
            String target = partition == null ? topic : topic + "-" + partition;
            String impact = "Produce one " + normalizePayloadFormat(request.format()) + " test message to " + target;
            if (request.preview()) {
                return previewResult(action, target, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("PRODUCE", target));
            KafkaManagementProducerFactory factory = producerFactory;
            if (factory == null) {
                throw new IllegalStateException("Kafka management producer factory is unavailable");
            }
            try (Producer<byte[], byte[]> producer = factory.createProducer()) {
                ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(
                    topic,
                    partition,
                    key,
                    value,
                    producerHeaders(request.headers())
                );
                RecordMetadata metadata = producer.send(record).get(ADMIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                producer.flush();
                return appliedResult(action, metadata.topic() + "-" + metadata.partition(), impact + " at offset " + metadata.offset());
            }
        });
    }

    Section<ActionResult> deleteConsumerGroup(DeleteConsumerGroupRequest request) {
        return section(() -> {
            String groupId = requireName(request.groupId(), "Consumer group");
            String action = "consumer-groups.delete";
            guardWrite(action, true);
            ConsumerGroupDescription description = requireInactiveConsumerGroup(groupId);
            String impact = "Delete inactive consumer group " + description.groupId();
            if (request.preview()) {
                return previewResult(action, groupId, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("DELETE GROUP", groupId));
            await(adminClient.deleteConsumerGroups(List.of(groupId), new DeleteConsumerGroupsOptions()).all());
            return appliedResult(action, groupId, impact);
        });
    }

    Section<ActionResult> resetConsumerGroupOffsets(ResetOffsetsRequest request) {
        return section(() -> {
            String groupId = requireName(request.groupId(), "Consumer group");
            String action = "consumer-groups.reset-offsets";
            guardWrite(action, false);
            requireInactiveConsumerGroup(groupId);
            Map<TopicPartition, OffsetAndMetadata> currentOffsets = consumerGroupOffsets(List.of(groupId))
                .getOrDefault(groupId, Map.of());
            List<TopicPartition> partitions = resetPartitions(request, currentOffsets.keySet());
            if (partitions.isEmpty()) {
                throw new IllegalArgumentException("At least one committed or requested partition is required");
            }
            Map<TopicPartition, OffsetAndMetadata> newOffsets = resetOffsets(request, partitions);
            String target = groupId + " " + partitions.stream()
                .map(KafkaClusterService::topicPartitionLabel)
                .collect(Collectors.joining(", "));
            String impact = "Reset " + partitions.size() + " offsets for inactive consumer group " + groupId
                + " to " + normalizeResetTarget(request.target());
            if (request.preview()) {
                return previewResult(action, target, impact);
            }
            requireConfirmation(request.confirmation(), confirmation("RESET OFFSETS", groupId));
            await(adminClient.alterConsumerGroupOffsets(
                groupId,
                newOffsets,
                new AlterConsumerGroupOffsetsOptions()
            ).all());
            return appliedResult(action, target, impact);
        });
    }

    Section<ActionResult> pauseAppConsumer(AppConsumerActionRequest request) {
        return appConsumerAction("app-consumers.pause", "PAUSE", request, true);
    }

    Section<ActionResult> resumeAppConsumer(AppConsumerActionRequest request) {
        return appConsumerAction("app-consumers.resume", "RESUME", request, false);
    }

    private Section<ActionResult> appConsumerAction(String action,
                                                    String confirmationAction,
                                                    AppConsumerActionRequest request,
                                                    boolean pause) {
        return section(() -> {
            String consumerId = requireName(request.consumerId(), "Consumer id");
            guardWrite(action, false);
            ConsumerRegistry registry = consumerRegistry;
            if (registry == null) {
                throw new IllegalStateException("Micronaut Kafka ConsumerRegistry is unavailable");
            }
            List<TopicPartition> partitions = request.partitions() == null ? List.of() : request.partitions().stream()
                .map(KafkaClusterService::toTopicPartition)
                .sorted(KafkaClusterService::compareTopicPartitions)
                .toList();
            String target = partitions.isEmpty() ? consumerId : consumerId + " " + partitions.stream()
                .map(KafkaClusterService::topicPartitionLabel)
                .collect(Collectors.joining(", "));
            String impact = (pause ? "Pause" : "Resume") + " app consumer " + target;
            if (request.preview()) {
                return previewResult(action, target, impact);
            }
            requireConfirmation(request.confirmation(), confirmation(confirmationAction, target));
            if (partitions.isEmpty()) {
                if (pause) {
                    registry.pause(consumerId);
                } else {
                    registry.resume(consumerId);
                }
            } else if (pause) {
                registry.pause(consumerId, partitions);
            } else {
                registry.resume(consumerId, partitions);
            }
            return appliedResult(action, target, impact);
        });
    }

    private void guardWrite(String action, boolean destructive) {
        if (!writeConfiguration.isEnabled()) {
            throw new IllegalStateException("Kafka writes are disabled. Set " + KafkaClusterWriteConfiguration.PREFIX + ".enabled=true to enable write endpoints.");
        }
        if (!writeConfiguration.actionEnabled(action)) {
            throw new IllegalStateException("Kafka write action is disabled: " + action);
        }
        if (destructive && !writeConfiguration.isDestructiveEnabled()) {
            throw new IllegalStateException("Kafka destructive actions are disabled. Set " + KafkaClusterWriteConfiguration.PREFIX + ".destructive-enabled=true to enable this action.");
        }
    }

    private static ActionResult previewResult(String action, String target, String impact) {
        return new ActionResult(false, action, target, impact, List.of());
    }

    private static ActionResult appliedResult(String action, String target, String impact) {
        return new ActionResult(true, action, target, impact, List.of());
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static void validatePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be greater than 0");
        }
    }

    private static String confirmation(String action, String target) {
        return action + " " + target;
    }

    private static void requireConfirmation(@Nullable String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Confirmation must exactly match: " + expected);
        }
    }

    private static byte[] payloadBytes(String value, String format, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = normalizePayloadFormat(format);
        if ("json".equals(normalized)) {
            String trimmed = value.trim();
            if (!isJsonCandidate(trimmed)) {
                throw new IllegalArgumentException(label + " must be a JSON object or array");
            }
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String normalizePayloadFormat(String format) {
        String normalized = format == null ? "string" : format.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "string", "json" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported payload format: " + format);
        };
    }

    private static List<Header> producerHeaders(@Nullable List<MessageHeaderInput> headers) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }
        return headers.stream()
            .map(header -> new RecordHeader(
                requireName(header.key(), "Header key"),
                header.value() == null ? null : header.value().getBytes(StandardCharsets.UTF_8)
            ))
            .map(Header.class::cast)
            .toList();
    }

    private ConsumerGroupDescription requireInactiveConsumerGroup(String groupId) throws Exception {
        ConsumerGroupDescription description = describeConsumerGroups(List.of(groupId)).get(groupId);
        if (description == null) {
            throw new IllegalArgumentException("Consumer group not found or not authorized: " + groupId);
        }
        if (description.groupState() != GroupState.EMPTY && !description.members().isEmpty()) {
            throw new IllegalStateException("Consumer group must be inactive before this operation: " + groupId);
        }
        return description;
    }

    private Map<TopicPartition, OffsetAndMetadata> resetOffsets(ResetOffsetsRequest request,
                                                                List<TopicPartition> partitions) throws Exception {
        String target = normalizeResetTarget(request.target());
        return switch (target) {
            case "earliest" -> offsetsForReset(partitions, OffsetSpec.earliest());
            case "latest" -> offsetsForReset(partitions, OffsetSpec.latest());
            case "offset" -> {
                if (request.offset() == null || request.offset() < 0) {
                    throw new IllegalArgumentException("Offset must be greater than or equal to 0");
                }
                yield partitions.stream()
                    .collect(Collectors.toMap(
                        partition -> partition,
                        partition -> new OffsetAndMetadata(request.offset()),
                        (first, second) -> first,
                        LinkedHashMap::new
                    ));
            }
            case "timestamp" -> {
                if (request.timestamp() == null || request.timestamp() < 0) {
                    throw new IllegalArgumentException("Timestamp must be greater than or equal to 0");
                }
                yield offsetsForReset(partitions, OffsetSpec.forTimestamp(request.timestamp()));
            }
            default -> throw new IllegalArgumentException("Unsupported reset target: " + request.target());
        };
    }

    private Map<TopicPartition, OffsetAndMetadata> offsetsForReset(List<TopicPartition> partitions,
                                                                   OffsetSpec offsetSpec) throws Exception {
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets = listOffsets(partitions, offsetSpec);
        Map<TopicPartition, OffsetAndMetadata> result = new LinkedHashMap<>();
        for (TopicPartition partition : partitions) {
            ListOffsetsResult.ListOffsetsResultInfo offset = offsets.get(partition);
            if (offset != null) {
                result.put(partition, new OffsetAndMetadata(offset.offset()));
            }
        }
        return result;
    }

    private static String normalizeResetTarget(String target) {
        return switch ((target == null ? "" : target).toLowerCase(Locale.ROOT)) {
            case "earliest", "latest", "offset", "timestamp" -> target.toLowerCase(Locale.ROOT);
            default -> throw new IllegalArgumentException("Unsupported reset target: " + target);
        };
    }

    private static List<TopicPartition> resetPartitions(ResetOffsetsRequest request,
                                                        Collection<TopicPartition> committedPartitions) {
        if (request.topic() != null && !request.topic().isBlank()) {
            if (request.partition() == null || request.partition() < 0) {
                throw new IllegalArgumentException("Partition must be greater than or equal to 0");
            }
            return List.of(new TopicPartition(request.topic(), request.partition()));
        }
        return committedPartitions.stream()
            .sorted(KafkaClusterService::compareTopicPartitions)
            .toList();
    }

    private static TopicPartition toTopicPartition(TopicPartitionInput input) {
        if (input.partition() < 0) {
            throw new IllegalArgumentException("Partition must be greater than or equal to 0");
        }
        return new TopicPartition(requireName(input.topic(), "Topic"), input.partition());
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

    private AppConsumer toAppConsumer(String consumerId) {
        ConsumerRegistry registry = consumerRegistry;
        if (registry == null) {
            throw new IllegalStateException("Micronaut Kafka ConsumerRegistry is unavailable");
        }
        List<String> subscriptions = registry.getConsumerSubscription(consumerId).stream()
            .sorted()
            .toList();
        List<AppConsumerAssignment> assignments = registry.getConsumerAssignment(consumerId).stream()
            .sorted(KafkaClusterService::compareTopicPartitions)
            .map(partition -> new AppConsumerAssignment(
                partition.topic(),
                partition.partition(),
                registry.isPaused(consumerId, List.of(partition))
            ))
            .toList();
        return new AppConsumer(
            consumerId,
            subscriptions,
            assignments,
            registry.isPaused(consumerId)
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
