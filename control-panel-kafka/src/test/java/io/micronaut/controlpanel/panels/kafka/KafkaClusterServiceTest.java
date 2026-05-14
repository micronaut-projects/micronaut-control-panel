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
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Controller;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.admin.AlterConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.CreatePartitionsOptions;
import org.apache.kafka.clients.admin.CreatePartitionsResult;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsOptions;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.clients.admin.ListGroupsResult;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.MemberAssignment;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.GroupType;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class KafkaClusterServiceTest {

    private static final Node BROKER_0 = new Node(0, "localhost", 9092, "rack-a");
    private static final Node BROKER_1 = new Node(1, "localhost", 9093, "rack-b");

    @Test
    void overviewReturnsClusterCounts() {
        AdminClient admin = mock(AdminClient.class);
        mockCluster(admin);
        mockTopics(admin, Map.of(
            "orders", topic("orders", false, partition(0, BROKER_0, List.of(BROKER_0, BROKER_1), List.of(BROKER_0, BROKER_1))),
            "payments", topic("payments", false, partition(0, BROKER_1, List.of(BROKER_1), List.of(BROKER_1)))
        ));
        mockConsumerGroups(admin, 2);
        mockConfigs(admin, Map.of(
            brokerResource(0), config(entry("num.partitions", "3"), entry("ssl.truststore.password", "secret")),
            brokerResource(1), config(entry("num.partitions", "3"))
        ));

        var section = new KafkaClusterService(admin).overview();

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals("cluster-a", section.data().clusterId());
        assertEquals(2, section.data().brokerCount());
        assertEquals(2, section.data().topicCount());
        assertEquals(2, section.data().partitionCount());
        assertEquals(2, section.data().consumerGroupCount());
        assertEquals("3", section.data().config().get("num.partitions"));
        assertFalse(section.data().config().containsKey("ssl.truststore.password"));
    }

    @Test
    void brokersReportLeaderReplicaAndUnderReplicatedCounts() {
        AdminClient admin = mock(AdminClient.class);
        mockCluster(admin);
        mockTopics(admin, Map.of(
            "orders", topic("orders", false,
                partition(0, BROKER_0, List.of(BROKER_0, BROKER_1), List.of(BROKER_0)),
                partition(1, BROKER_1, List.of(BROKER_0, BROKER_1), List.of(BROKER_0, BROKER_1)))
        ));
        mockConfigs(admin, Map.of(
            brokerResource(0), config(entry("log.retention.hours", "168")),
            brokerResource(1), config(entry("log.retention.hours", "168"))
        ));

        var section = new KafkaClusterService(admin).brokers();

        assertNull(section.error());
        var brokers = section.data();
        assertNotNull(brokers);
        assertEquals(2, brokers.size());
        assertTrue(brokers.get(0).controller());
        assertEquals(1, brokers.get(0).leaderPartitions());
        assertEquals(2, brokers.get(0).replicaPartitions());
        assertEquals(1, brokers.get(0).underReplicatedPartitions());
        assertEquals(1, brokers.get(1).leaderPartitions());
        assertEquals(2, brokers.get(1).replicaPartitions());
        assertEquals(1, brokers.get(1).underReplicatedPartitions());
    }

    @Test
    void topicsSupportSearchPagingAndInternalFiltering() {
        AdminClient admin = mock(AdminClient.class);
        mockTopics(admin, Map.of(
            "__consumer_offsets", topic("__consumer_offsets", true, partition(0, BROKER_0, List.of(BROKER_0), List.of(BROKER_0))),
            "orders", topic("orders", false, partition(0, BROKER_0, List.of(BROKER_0), List.of(BROKER_0))),
            "order-events", topic("order-events", false, partition(0, BROKER_0, List.of(BROKER_0), List.of(BROKER_0))),
            "payments", topic("payments", false, partition(0, BROKER_0, List.of(BROKER_0), List.of(BROKER_0)))
        ));
        mockConfigs(admin, Map.of(
            topicResource("__consumer_offsets"), config(entry("cleanup.policy", "compact")),
            topicResource("orders"), config(entry("cleanup.policy", "delete")),
            topicResource("order-events"), config(entry("cleanup.policy", "delete")),
            topicResource("payments"), config(entry("cleanup.policy", "delete"))
        ));

        var section = new KafkaClusterService(admin).topics("order", false, 1, 1);

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals(4, section.data().recordsTotal());
        assertEquals(2, section.data().recordsFiltered());
        assertEquals(List.of("orders"), section.data().topics().stream().map(KafkaClusterResponse.TopicSummary::name).toList());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Collection<ConfigResource>> captor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(admin).describeConfigs(captor.capture(), any(DescribeConfigsOptions.class));
        assertEquals(List.of(topicResource("orders")), captor.getValue().stream().toList());
    }

    @Test
    void topicsCapPageLengthBeforeFetchingConfigs() {
        AdminClient admin = mock(AdminClient.class);
        Map<String, TopicDescription> topics = new LinkedHashMap<>();
        Map<ConfigResource, Config> configs = new LinkedHashMap<>();
        for (int i = 0; i < 101; i++) {
            String topicName = "topic-%03d".formatted(i);
            topics.put(topicName, topic(topicName, false, partition(0, BROKER_0, List.of(BROKER_0), List.of(BROKER_0))));
            configs.put(topicResource(topicName), config(entry("cleanup.policy", "delete")));
        }
        mockTopics(admin, topics);
        mockConfigs(admin, configs);

        var section = new KafkaClusterService(admin).topics(null, false, 0, 1_000);

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals(100, section.data().length());
        assertEquals(101, section.data().recordsTotal());
        assertEquals(101, section.data().recordsFiltered());
        assertEquals(100, section.data().topics().size());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Collection<ConfigResource>> captor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(admin).describeConfigs(captor.capture(), any(DescribeConfigsOptions.class));
        assertEquals(100, captor.getValue().size());
        assertFalse(captor.getValue().contains(topicResource("topic-100")));
    }

    @Test
    void topicDetailIncludesOffsetsAndFiltersSensitiveConfigs() {
        AdminClient admin = mock(AdminClient.class);
        TopicDescription orders = topic("orders", false,
            partition(0, BROKER_0, List.of(BROKER_0, BROKER_1), List.of(BROKER_0, BROKER_1)));
        mockTopicDetail(admin, orders);
        mockConfigs(admin, Map.of(topicResource("orders"), config(
            entry("cleanup.policy", "delete"),
            entry("retention.ms", "60000"),
            sensitiveEntry("password", "secret"),
            entry("sasl.jaas.config", "secret")
        )));
        mockOffsets(admin,
            Map.of(new TopicPartition("orders", 0), new ListOffsetsResult.ListOffsetsResultInfo(2L, 0L, Optional.empty())),
            Map.of(new TopicPartition("orders", 0), new ListOffsetsResult.ListOffsetsResultInfo(7L, 0L, Optional.empty()))
        );

        var section = new KafkaClusterService(admin).topic("orders");

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals("orders", section.data().topic().name());
        assertEquals(2L, section.data().partitions().get(0).beginningOffset());
        assertEquals(7L, section.data().partitions().get(0).endOffset());
        assertEquals(5L, section.data().partitions().get(0).sizeEstimate());
        assertEquals("delete", section.data().topic().cleanupPolicy());
        assertFalse(section.data().topic().config().containsKey("password"));
        assertFalse(section.data().topic().config().containsKey("sasl.jaas.config"));
    }

    @Test
    void sectionReportsAdminFailuresWithoutThrowing() {
        AdminClient admin = mock(AdminClient.class);
        when(admin.listTopics(any(ListTopicsOptions.class))).thenThrow(new IllegalStateException("not authorized"));

        var section = new KafkaClusterService(admin).topics(null, false, 0, 25);

        assertNull(section.data());
        assertEquals("not authorized", section.error());
    }

    @Test
    void topicDetailReportsMissingTopicClearly() {
        AdminClient admin = mock(AdminClient.class);
        mockTopicDetail(admin, Map.of());

        var section = new KafkaClusterService(admin).topic("missing-topic");

        assertNull(section.data());
        assertEquals("Topic not found or not authorized: missing-topic", section.error());
    }

    @Test
    void consumerGroupsReturnLagSummaries() {
        AdminClient admin = mock(AdminClient.class);
        ConsumerGroupDescription group = consumerGroup(
            "orders-service",
            GroupState.STABLE,
            "range",
            member("consumer-1", "client-1", "/127.0.0.1",
                new TopicPartition("orders", 0),
                new TopicPartition("orders", 1))
        );
        mockConsumerGroupList(admin, "orders-service");
        mockConsumerGroupDescriptions(admin, Map.of("orders-service", group));
        mockConsumerGroupOffsets(admin, Map.of("orders-service", Map.of(
            new TopicPartition("orders", 0), new OffsetAndMetadata(4L),
            new TopicPartition("orders", 1), new OffsetAndMetadata(6L)
        )));
        mockOffsets(admin, Map.of(
            new TopicPartition("orders", 0), new ListOffsetsResult.ListOffsetsResultInfo(10L, 0L, Optional.empty()),
            new TopicPartition("orders", 1), new ListOffsetsResult.ListOffsetsResultInfo(9L, 0L, Optional.empty())
        ));

        var section = new KafkaClusterService(admin).consumerGroups();

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals(1, section.data().size());
        var summary = section.data().getFirst();
        assertEquals("orders-service", summary.groupId());
        assertEquals("Stable", summary.state());
        assertEquals("range", summary.protocol());
        assertEquals(1, summary.members());
        assertEquals(2, summary.assignedPartitions());
        assertEquals(2, summary.committedPartitions());
        assertEquals(9L, summary.totalLag());
    }

    @Test
    void consumerGroupDetailIncludesMembersAssignmentsOffsetsAndLag() {
        AdminClient admin = mock(AdminClient.class);
        ConsumerGroupDescription group = consumerGroup(
            "orders-service",
            GroupState.STABLE,
            "range",
            member("consumer-2", "client-2", "/127.0.0.2", new TopicPartition("orders", 1)),
            member("consumer-1", "client-1", "/127.0.0.1", new TopicPartition("orders", 0))
        );
        mockConsumerGroupDescriptions(admin, Map.of("orders-service", group));
        mockConsumerGroupOffsets(admin, Map.of("orders-service", Map.of(
            new TopicPartition("orders", 0), new OffsetAndMetadata(4L),
            new TopicPartition("orders", 1), new OffsetAndMetadata(7L),
            new TopicPartition("payments", 0), new OffsetAndMetadata(3L)
        )));
        mockOffsets(admin, Map.of(
            new TopicPartition("orders", 0), new ListOffsetsResult.ListOffsetsResultInfo(10L, 0L, Optional.empty()),
            new TopicPartition("orders", 1), new ListOffsetsResult.ListOffsetsResultInfo(9L, 0L, Optional.empty()),
            new TopicPartition("payments", 0), new ListOffsetsResult.ListOffsetsResultInfo(8L, 0L, Optional.empty())
        ));

        var section = new KafkaClusterService(admin).consumerGroup("orders-service");

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals("orders-service", section.data().group().groupId());
        assertEquals(13L, section.data().group().totalLag());
        assertEquals(List.of("consumer-1", "consumer-2"), section.data().members().stream()
            .map(KafkaClusterResponse.ConsumerGroupMember::consumerId)
            .toList());
        assertEquals(List.of("orders-0"), section.data().members().getFirst().assignment());
        assertEquals(List.of("orders:0", "orders:1", "payments:0"), section.data().partitions().stream()
            .map(partition -> partition.topic() + ":" + partition.partition())
            .toList());
        assertTrue(section.data().partitions().getFirst().assigned());
        assertEquals(4L, section.data().partitions().getFirst().committedOffset());
        assertEquals(10L, section.data().partitions().getFirst().endOffset());
        assertEquals(6L, section.data().partitions().getFirst().lag());
        assertFalse(section.data().partitions().get(2).assigned());
        assertEquals(5L, section.data().partitions().get(2).lag());
    }

    @Test
    void consumerGroupDetailReportsMissingGroupClearly() {
        AdminClient admin = mock(AdminClient.class);
        mockConsumerGroupDescriptions(admin, Map.of());

        var section = new KafkaClusterService(admin).consumerGroup("missing-group");

        assertNull(section.data());
        assertEquals("Consumer group not found or not authorized: missing-group", section.error());
    }

    @Test
    void appConsumersReturnRegistrySubscriptionsAssignmentsAndPauseState() {
        AdminClient admin = mock(AdminClient.class);
        ConsumerRegistry registry = mock(ConsumerRegistry.class);
        TopicPartition ordersZero = new TopicPartition("orders", 0);
        TopicPartition paymentsOne = new TopicPartition("payments", 1);
        when(registry.getConsumerIds()).thenReturn(Set.of("payments-consumer", "orders-consumer"));
        when(registry.getConsumerSubscription("orders-consumer")).thenReturn(Set.of("orders", "payments"));
        when(registry.getConsumerAssignment("orders-consumer")).thenReturn(Set.of(paymentsOne, ordersZero));
        when(registry.isPaused("orders-consumer")).thenReturn(true);
        when(registry.isPaused("orders-consumer", List.of(ordersZero))).thenReturn(false);
        when(registry.isPaused("orders-consumer", List.of(paymentsOne))).thenReturn(true);
        when(registry.getConsumerSubscription("payments-consumer")).thenReturn(Set.of());
        when(registry.getConsumerAssignment("payments-consumer")).thenReturn(Set.of());
        when(registry.isPaused("payments-consumer")).thenReturn(false);

        var section = new KafkaClusterService(admin, registry).appConsumers();

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals(List.of("orders-consumer", "payments-consumer"), section.data().stream()
            .map(KafkaClusterResponse.AppConsumer::id)
            .toList());
        KafkaClusterResponse.AppConsumer ordersConsumer = section.data().getFirst();
        assertEquals(List.of("orders", "payments"), ordersConsumer.subscriptions());
        assertTrue(ordersConsumer.paused());
        assertEquals(List.of("orders:0:false", "payments:1:true"), ordersConsumer.assignments().stream()
            .map(assignment -> assignment.topic() + ":" + assignment.partition() + ":" + assignment.paused())
            .toList());
    }

    @Test
    void appConsumersAreEmptyWhenConsumerRegistryIsUnavailable() {
        AdminClient admin = mock(AdminClient.class);

        var section = new KafkaClusterService(admin).appConsumers();

        assertNull(section.error());
        assertEquals(List.of(), section.data());
    }

    @Test
    void messageBrowserReadsFromBeginningWithSafeRenderingAndNoCommits() {
        AdminClient admin = mock(AdminClient.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);
        KafkaMessageBrowserConsumerFactory factory = mock(KafkaMessageBrowserConsumerFactory.class);
        TopicPartition partition = new TopicPartition("orders", 0);
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.position(any(TopicPartition.class), any())).thenReturn(0L);
        when(consumer.poll(any())).thenReturn(records(partition,
            record(0L, "{\"id\":1}".getBytes(StandardCharsets.UTF_8), "order".getBytes(StandardCharsets.UTF_8)),
            record(1L, new byte[] {(byte) 0xff, 0x01}, null)
        ), ConsumerRecords.empty());

        var section = new KafkaClusterService(admin, factory).messages("orders", 0, "beginning", null, null, 25);

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals("beginning", section.data().mode());
        assertEquals(0L, section.data().startOffset());
        assertEquals(2, section.data().records().size());
        KafkaClusterResponse.MessageRecord jsonRecord = section.data().records().stream()
            .filter(record -> record.offset() == 0L)
            .findFirst()
            .orElseThrow();
        KafkaClusterResponse.MessageRecord binaryRecord = section.data().records().stream()
            .filter(record -> record.offset() == 1L)
            .findFirst()
            .orElseThrow();
        assertEquals("utf8", jsonRecord.key().format());
        assertEquals("json", jsonRecord.value().format());
        assertEquals("""
            {
              "id": 1
            }""", jsonRecord.value().text());
        assertEquals("base64", binaryRecord.value().format());
        assertEquals("/wE=", binaryRecord.value().base64());
        assertEquals("utf8", jsonRecord.headers().getFirst().value().format());
        verify(consumer).assign(List.of(partition));
        verify(consumer).seekToBeginning(List.of(partition));
        verify(consumer, never()).commitSync();
        verify(consumer, never()).commitAsync();
        verify(consumer).close();
    }

    @Test
    void messageBrowserReadsLatestWindowWithCappedLimit() {
        AdminClient admin = mock(AdminClient.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);
        KafkaMessageBrowserConsumerFactory factory = mock(KafkaMessageBrowserConsumerFactory.class);
        TopicPartition partition = new TopicPartition("orders", 0);
        when(factory.createConsumer()).thenReturn(consumer);
        when(consumer.beginningOffsets(any(Collection.class), any())).thenReturn(Map.of(partition, 5L));
        when(consumer.endOffsets(any(Collection.class), any())).thenReturn(Map.of(partition, 150L));
        when(consumer.poll(any())).thenReturn(records(partition,
            record(50L, "first".getBytes(StandardCharsets.UTF_8), null),
            record(149L, "last".getBytes(StandardCharsets.UTF_8), null),
            record(150L, "future".getBytes(StandardCharsets.UTF_8), null)
        ));

        var section = new KafkaClusterService(admin, factory).messages("orders", 0, "latest", null, null, 1_000);

        assertNull(section.error());
        assertNotNull(section.data());
        assertEquals(100, section.data().limit());
        assertEquals(50L, section.data().startOffset());
        assertEquals(150L, section.data().endOffset());
        assertEquals(List.of(50L, 149L), section.data().records().stream()
            .map(KafkaClusterResponse.MessageRecord::offset)
            .toList());
        verify(consumer).seek(partition, 50L);
        verify(consumer, never()).commitSync();
        verify(consumer).close();
    }

    @Test
    void messageBrowserReadsFromExplicitOffsetAndTimestamp() {
        AdminClient admin = mock(AdminClient.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> offsetConsumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> timestampConsumer = mock(Consumer.class);
        KafkaMessageBrowserConsumerFactory factory = mock(KafkaMessageBrowserConsumerFactory.class);
        TopicPartition partition = new TopicPartition("orders", 0);
        when(factory.createConsumer()).thenReturn(offsetConsumer, timestampConsumer);
        when(offsetConsumer.poll(any())).thenReturn(records(partition,
            record(42L, "offset".getBytes(StandardCharsets.UTF_8), null)
        ));
        when(timestampConsumer.offsetsForTimes(anyMap(), any())).thenReturn(Map.of(partition, new OffsetAndTimestamp(77L, 1234L)));
        when(timestampConsumer.poll(any())).thenReturn(records(partition,
            record(77L, "timestamp".getBytes(StandardCharsets.UTF_8), null)
        ));

        var offsetSection = new KafkaClusterService(admin, factory).messages("orders", 0, "offset", 42L, null, 10);
        var timestampSection = new KafkaClusterService(admin, factory).messages("orders", 0, "timestamp", null, 1234L, 10);

        assertNull(offsetSection.error());
        assertNull(timestampSection.error());
        assertEquals(42L, offsetSection.data().startOffset());
        assertEquals(77L, timestampSection.data().startOffset());
        verify(offsetConsumer).seek(partition, 42L);
        verify(timestampConsumer).seek(partition, 77L);
        verify(offsetConsumer, never()).commitSync();
        verify(timestampConsumer, never()).commitSync();
    }

    @Test
    void messageBrowserFallsBackToEndWhenLatestOrTimestampOffsetsAreUnavailable() {
        AdminClient admin = mock(AdminClient.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> latestConsumer = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> timestampConsumer = mock(Consumer.class);
        KafkaMessageBrowserConsumerFactory factory = mock(KafkaMessageBrowserConsumerFactory.class);
        TopicPartition partition = new TopicPartition("orders", 0);
        when(factory.createConsumer()).thenReturn(latestConsumer, timestampConsumer);
        when(latestConsumer.beginningOffsets(any(Collection.class), any())).thenReturn(Map.of());
        when(latestConsumer.endOffsets(any(Collection.class), any())).thenReturn(Map.of());
        when(latestConsumer.position(any(TopicPartition.class), any())).thenReturn(12L);
        when(latestConsumer.poll(any())).thenReturn(ConsumerRecords.empty());
        when(timestampConsumer.offsetsForTimes(anyMap(), any())).thenReturn(Map.of());
        when(timestampConsumer.position(any(TopicPartition.class), any())).thenReturn(15L);
        when(timestampConsumer.poll(any())).thenReturn(ConsumerRecords.empty());

        var latestSection = new KafkaClusterService(admin, factory).messages("orders", 0, "latest", null, null, 10);
        var timestampSection = new KafkaClusterService(admin, factory).messages("orders", 0, "timestamp", null, 1234L, 10);

        assertNull(latestSection.error());
        assertEquals(12L, latestSection.data().startOffset());
        assertNull(timestampSection.error());
        assertEquals(15L, timestampSection.data().startOffset());
        verify(latestConsumer).seekToEnd(List.of(partition));
        verify(timestampConsumer).seekToEnd(List.of(partition));
    }

    @Test
    void messageBrowserReportsInvalidRequestsAsSectionErrors() {
        AdminClient admin = mock(AdminClient.class);
        KafkaMessageBrowserConsumerFactory factory = mock(KafkaMessageBrowserConsumerFactory.class);

        var section = new KafkaClusterService(admin, factory).messages("orders", -1, "offset", null, null, 25);

        assertNull(section.data());
        assertEquals("Partition must be greater than or equal to 0", section.error());
    }

    @Test
    void messageBrowserConsumerConfigUsesTemporaryNonCommittingConsumer() {
        Properties defaults = new Properties();
        defaults.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        defaults.put(ConsumerConfig.GROUP_ID_CONFIG, "application-group");
        defaults.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        Properties config = KafkaMessageBrowserConsumerFactory.browserConsumerConfig(defaults);

        assertEquals("localhost:9092", config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertFalse(config.containsKey(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals("false", config.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertNotNull(config.get(ConsumerConfig.CLIENT_ID_CONFIG));
    }

    @Test
    void writeCapabilitiesAreDefaultOff() {
        AdminClient admin = mock(AdminClient.class);

        var section = new KafkaClusterService(admin).writeCapabilities();

        assertNull(section.error());
        assertNotNull(section.data());
        assertFalse(section.data().writesEnabled());
        assertFalse(section.data().destructiveEnabled());
        assertTrue(section.data().actions().get("topics.create"));
    }

    @Test
    void createTopicRejectsWhenWritesAreDisabled() {
        AdminClient admin = mock(AdminClient.class);

        var section = new KafkaClusterService(admin).createTopic(new KafkaClusterResponse.CreateTopicRequest(
            "orders",
            1,
            (short) 1,
            Map.of(),
            false,
            "CREATE orders"
        ));

        assertNull(section.data());
        assertEquals("Kafka writes are disabled. Set micronaut.control-panel.kafka.writes.enabled=true to enable write endpoints.", section.error());
        verify(admin, never()).createTopics(any(Collection.class), any(CreateTopicsOptions.class));
    }

    @Test
    void destructiveActionsRequireSeparateGate() {
        AdminClient admin = mock(AdminClient.class);

        var section = new KafkaClusterService(admin, null, null, null, writeConfig(true, false))
            .deleteTopic(new KafkaClusterResponse.DeleteTopicRequest("orders", false, "DELETE orders"));

        assertNull(section.data());
        assertEquals("Kafka destructive actions are disabled. Set micronaut.control-panel.kafka.writes.destructive-enabled=true to enable this action.", section.error());
        verify(admin, never()).deleteTopics(any(TopicCollection.class), any(DeleteTopicsOptions.class));
    }

    @Test
    void writePreviewActionsReturnImpactWithoutMutatingKafkaOrIntegrations() {
        AdminClient admin = mock(AdminClient.class);
        FakeIntegrationClient client = new FakeIntegrationClient(Map.of());
        KafkaClusterService service = new KafkaClusterService(
            admin,
            client,
            integrationConfig("http://registry", "http://connect", null),
            writeConfig(true, true)
        );

        assertPreview(service.updateTopicConfig(new KafkaClusterResponse.UpdateTopicConfigRequest(
            "orders",
            Map.of("cleanup.policy", "compact"),
            true,
            null
        )), KafkaClusterService.ACTION_UPDATE_TOPIC_CONFIG);
        assertPreview(service.increasePartitions(new KafkaClusterResponse.IncreasePartitionsRequest(
            "orders",
            6,
            true,
            null
        )), KafkaClusterService.ACTION_INCREASE_PARTITIONS);
        assertPreview(service.deleteTopic(new KafkaClusterResponse.DeleteTopicRequest(
            "orders",
            true,
            null
        )), KafkaClusterService.ACTION_DELETE_TOPIC);
        assertPreview(service.registerSchema(new KafkaClusterResponse.RegisterSchemaRequest(
            "orders-value",
            "{}",
            "JSON",
            true,
            null
        )), KafkaClusterService.ACTION_REGISTER_SCHEMA);
        assertPreview(service.updateSchemaCompatibility(new KafkaClusterResponse.UpdateSchemaCompatibilityRequest(
            "orders-value",
            "BACKWARD",
            true,
            null
        )), KafkaClusterService.ACTION_UPDATE_SCHEMA_COMPATIBILITY);
        assertPreview(service.deleteSchemaSubject(new KafkaClusterResponse.DeleteSchemaSubjectRequest(
            "orders-value",
            true,
            null
        )), KafkaClusterService.ACTION_DELETE_SCHEMA_SUBJECT);
        assertPreview(service.deleteSchemaVersion(new KafkaClusterResponse.DeleteSchemaVersionRequest(
            "orders-value",
            2,
            true,
            null
        )), KafkaClusterService.ACTION_DELETE_SCHEMA_VERSION);
        assertPreview(service.pauseConnector(new KafkaClusterResponse.ConnectorActionRequest(
            "jdbc-sink",
            true,
            null
        )), KafkaClusterService.ACTION_PAUSE_CONNECTOR);
        assertPreview(service.resumeConnector(new KafkaClusterResponse.ConnectorActionRequest(
            "jdbc-sink",
            true,
            null
        )), KafkaClusterService.ACTION_RESUME_CONNECTOR);
        assertPreview(service.restartConnector(new KafkaClusterResponse.ConnectorActionRequest(
            "jdbc-sink",
            true,
            null
        )), KafkaClusterService.ACTION_RESTART_CONNECTOR);
        assertPreview(service.restartConnectorTask(new KafkaClusterResponse.ConnectorTaskActionRequest(
            "jdbc-sink",
            0,
            true,
            null
        )), KafkaClusterService.ACTION_RESTART_CONNECTOR_TASK);
        assertPreview(service.updateConnectorConfig(new KafkaClusterResponse.UpdateConnectorConfigRequest(
            "jdbc-sink",
            Map.of("connector.class", "JdbcSinkConnector"),
            true,
            null
        )), KafkaClusterService.ACTION_UPDATE_CONNECTOR_CONFIG);
        assertPreview(service.deleteConnector(new KafkaClusterResponse.ConnectorActionRequest(
            "jdbc-sink",
            true,
            null
        )), KafkaClusterService.ACTION_DELETE_CONNECTOR);

        assertNull(client.lastAction());
        verify(admin, never()).deleteTopics(any(TopicCollection.class), any(DeleteTopicsOptions.class));
    }

    @Test
    void createTopicPreviewDoesNotMutateKafka() {
        AdminClient admin = mock(AdminClient.class);

        var section = new KafkaClusterService(admin, null, null, null, writeConfig(true, false))
            .createTopic(new KafkaClusterResponse.CreateTopicRequest(
                "orders",
                3,
                (short) 1,
                Map.of("cleanup.policy", "delete"),
                true,
                null
            ));

        assertNull(section.error());
        assertNotNull(section.data());
        assertFalse(section.data().applied());
        assertEquals("topics.create", section.data().action());
        assertEquals("orders", section.data().target());
        verify(admin, never()).createTopics(any(Collection.class), any(CreateTopicsOptions.class));
    }

    @Test
    void createTopicAppliesWithExactConfirmation() {
        AdminClient admin = mock(AdminClient.class);
        CreateTopicsResult result = mock(CreateTopicsResult.class);
        when(admin.createTopics(any(Collection.class), any(CreateTopicsOptions.class))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));

        var section = new KafkaClusterService(admin, null, null, null, writeConfig(true, false))
            .createTopic(new KafkaClusterResponse.CreateTopicRequest(
                "orders",
                3,
                (short) 1,
                Map.of("cleanup.policy", "delete"),
                false,
                "CREATE orders"
            ));

        assertNull(section.error());
        assertNotNull(section.data());
        assertTrue(section.data().applied());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(admin).createTopics(captor.capture(), any(CreateTopicsOptions.class));
        assertEquals("orders", ((org.apache.kafka.clients.admin.NewTopic) captor.getValue().iterator().next()).name());
    }

    @Test
    void topicWriteActionsApplyWithConfirmations() {
        AdminClient admin = mock(AdminClient.class);
        AlterConfigsResult alterConfigsResult = mock(AlterConfigsResult.class);
        CreatePartitionsResult createPartitionsResult = mock(CreatePartitionsResult.class);
        DeleteTopicsResult deleteTopicsResult = mock(DeleteTopicsResult.class);
        when(admin.incrementalAlterConfigs(anyMap(), any())).thenReturn(alterConfigsResult);
        when(alterConfigsResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.createPartitions(anyMap(), any(CreatePartitionsOptions.class))).thenReturn(createPartitionsResult);
        when(createPartitionsResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.deleteTopics(any(TopicCollection.class), any(DeleteTopicsOptions.class))).thenReturn(deleteTopicsResult);
        when(deleteTopicsResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        KafkaClusterService service = new KafkaClusterService(admin, null, null, null, writeConfig(true, true));

        var updateConfig = service.updateTopicConfig(new KafkaClusterResponse.UpdateTopicConfigRequest(
            "orders",
            Map.of("cleanup.policy", "compact", "retention.ms", "60000"),
            false,
            "UPDATE CONFIG orders"
        ));
        var increasePartitions = service.increasePartitions(new KafkaClusterResponse.IncreasePartitionsRequest(
            "orders",
            4,
            false,
            "INCREASE PARTITIONS orders"
        ));
        var deleteTopic = service.deleteTopic(new KafkaClusterResponse.DeleteTopicRequest(
            "orders",
            false,
            "DELETE orders"
        ));

        assertTrue(updateConfig.data().applied());
        assertTrue(increasePartitions.data().applied());
        assertTrue(deleteTopic.data().applied());
        verify(admin).incrementalAlterConfigs(anyMap(), any());
        verify(admin).createPartitions(anyMap(), any(CreatePartitionsOptions.class));
        verify(admin).deleteTopics(any(TopicCollection.class), any(DeleteTopicsOptions.class));
    }

    @Test
    void resetOffsetsAppliesExplicitOffsetForInactiveGroup() {
        AdminClient admin = mock(AdminClient.class);
        TopicPartition partition = new TopicPartition("orders", 0);
        mockConsumerGroupDescriptions(admin, Map.of("orders-service", consumerGroup(
            "orders-service",
            GroupState.EMPTY,
            "range"
        )));
        mockConsumerGroupOffsets(admin, Map.of("orders-service", Map.of(partition, new OffsetAndMetadata(20L))));
        AlterConsumerGroupOffsetsResult result = mock(AlterConsumerGroupOffsetsResult.class);
        when(admin.alterConsumerGroupOffsets(any(String.class), anyMap(), any())).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));

        var section = new KafkaClusterService(admin, null, null, null, writeConfig(true, false))
            .resetConsumerGroupOffsets(new KafkaClusterResponse.ResetOffsetsRequest(
                "orders-service",
                "offset",
                null,
                null,
                7L,
                null,
                false,
                "RESET OFFSETS orders-service"
            ));

        assertNull(section.error());
        assertTrue(section.data().applied());
        verify(admin).alterConsumerGroupOffsets(any(String.class), anyMap(), any());
    }

    @Test
    void resetOffsetsRequiresInactiveConsumerGroup() {
        AdminClient admin = mock(AdminClient.class);
        mockConsumerGroupDescriptions(admin, Map.of("orders-service", consumerGroup(
            "orders-service",
            GroupState.STABLE,
            "range",
            member("consumer-1", "client-1", "/127.0.0.1", new TopicPartition("orders", 0))
        )));

        var section = new KafkaClusterService(admin, null, null, null, writeConfig(true, false))
            .resetConsumerGroupOffsets(new KafkaClusterResponse.ResetOffsetsRequest(
                "orders-service",
                "earliest",
                "orders",
                0,
                null,
                null,
                true,
                null
            ));

        assertNull(section.data());
        assertEquals("Consumer group must be inactive before this operation: orders-service", section.error());
    }

    @Test
    void appConsumerPauseUsesRegistryWhenEnabled() {
        AdminClient admin = mock(AdminClient.class);
        ConsumerRegistry registry = mock(ConsumerRegistry.class);

        var section = new KafkaClusterService(admin, registry, null, null, writeConfig(true, false))
            .pauseAppConsumer(new KafkaClusterResponse.AppConsumerActionRequest(
                "orders-consumer",
                List.of(new KafkaClusterResponse.TopicPartitionInput("orders", 0)),
                false,
                "PAUSE orders-consumer orders-0"
            ));

        assertNull(section.error());
        assertNotNull(section.data());
        assertTrue(section.data().applied());
        verify(registry).pause("orders-consumer", List.of(new TopicPartition("orders", 0)));
    }

    @Test
    void managementProducerConfigUsesIsolatedByteArrayProducer() {
        Properties defaults = new Properties();
        defaults.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        defaults.put(org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG, "application-transaction");

        Properties config = KafkaManagementProducerFactory.producerConfig(defaults);

        assertEquals("localhost:9092", config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(
            org.apache.kafka.common.serialization.ByteArraySerializer.class.getName(),
            config.get(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)
        );
        assertFalse(config.containsKey(org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG));
        assertNotNull(config.get(org.apache.kafka.clients.producer.ProducerConfig.CLIENT_ID_CONFIG));
    }

    @Test
    void produceMessageAppliesWithValidatedHeadersAndProducerMetadata() {
        AdminClient admin = mock(AdminClient.class);
        KafkaManagementProducerFactory factory = mock(KafkaManagementProducerFactory.class);
        @SuppressWarnings("unchecked")
        Producer<byte[], byte[]> producer = mock(Producer.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(factory.createProducer()).thenReturn(producer);
        when(metadata.topic()).thenReturn("orders");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(9L);
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(metadata));

        var section = new KafkaClusterService(admin, null, null, factory, writeConfig(true, false))
            .produceMessage(produceRequest(
                "orders",
                "order-1",
                "{\"id\":1}",
                List.of(new KafkaClusterResponse.MessageHeaderInput("trace", "abc"))
            ));

        assertNull(section.error());
        assertTrue(section.data().applied());
        assertTrue(section.data().impact().contains("offset 9"));
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<ProducerRecord<byte[], byte[]>> captor = ArgumentCaptor.forClass((Class) ProducerRecord.class);
        verify(producer).send(captor.capture());
        assertEquals("orders", captor.getValue().topic());
        assertEquals("trace", captor.getValue().headers().iterator().next().key());
        verify(producer).flush();
        verify(producer).close();
    }

    private static void assertPreview(KafkaClusterResponse.Section<KafkaClusterResponse.ActionResult> section, String action) {
        assertNull(section.error());
        assertNotNull(section.data());
        assertFalse(section.data().applied());
        assertEquals(action, section.data().action());
    }

    @Test
    void produceMessageRejectsOversizedValueBeforeCreatingProducerRecord() {
        var section = serviceWithProduceLimits().produceMessage(produceRequest("orders", null, "too-long", List.of()));

        assertNull(section.data());
        assertEquals("Value must be 4 bytes or less", section.error());
    }

    @Test
    void produceMessageRejectsOversizedKeyBeforeCreatingProducerRecord() {
        var section = serviceWithProduceLimits().produceMessage(produceRequest("orders", "wide-key", "ok", List.of()));

        assertNull(section.data());
        assertEquals("Key must be 3 bytes or less", section.error());
    }

    @Test
    void produceMessageRejectsTooManyHeadersBeforeCreatingProducerRecord() {
        var section = serviceWithProduceLimits().produceMessage(produceRequest(
            "orders",
            null,
            "ok",
            List.of(
                new KafkaClusterResponse.MessageHeaderInput("a", "1"),
                new KafkaClusterResponse.MessageHeaderInput("b", "2"),
                new KafkaClusterResponse.MessageHeaderInput("c", "3")
            )
        ));

        assertNull(section.data());
        assertEquals("Header count must be 2 or less", section.error());
    }

    @Test
    void produceMessageRejectsOversizedHeaderKeyBeforeCreatingProducerRecord() {
        var section = serviceWithProduceLimits().produceMessage(produceRequest(
            "orders",
            null,
            "ok",
            List.of(new KafkaClusterResponse.MessageHeaderInput("wide", "1"))
        ));

        assertNull(section.data());
        assertEquals("Header key must be 3 bytes or less", section.error());
    }

    @Test
    void produceMessageRejectsOversizedHeaderValueBeforeCreatingProducerRecord() {
        var section = serviceWithProduceLimits().produceMessage(produceRequest(
            "orders",
            null,
            "ok",
            List.of(new KafkaClusterResponse.MessageHeaderInput("ok", "wide"))
        ));

        assertNull(section.data());
        assertEquals("Header value must be 3 bytes or less", section.error());
    }

    @Test
    void optionalIntegrationsAreAbsentUntilConfigured() {
        AdminClient admin = mock(AdminClient.class);

        KafkaClusterService service = new KafkaClusterService(
            admin,
            new FakeIntegrationClient(Map.of()),
            new KafkaIntegrationConfiguration(),
            writeConfig(false, false)
        );

        assertFalse(service.schemaRegistry().data().configured());
        assertFalse(service.kafkaConnect().data().configured());
        assertFalse(service.ksqldb().data().configured());
    }

    @Test
    void schemaRegistryReadViewsLoadSubjectsVersionsAndDetails() {
        AdminClient admin = mock(AdminClient.class);
        KafkaIntegrationConfiguration configuration = integrationConfig("http://schema-registry", null, null);
        FakeIntegrationClient client = new FakeIntegrationClient(Map.of(
            "Schema Registry GET /subjects", KafkaClusterService.json("[\"orders-value\"]"),
            "Schema Registry GET /subjects/orders-value/versions", KafkaClusterService.json("[1,2]"),
            "Schema Registry GET /subjects/orders-value/versions/2", KafkaClusterService.json("""
                {"subject":"orders-value","version":2,"id":7,"schemaType":"AVRO","schema":"{\\"type\\":\\"record\\",\\"name\\":\\"Order\\"}","references":[{"name":"Money","subject":"money-value","version":1}]}
                """),
            "Schema Registry GET /config/orders-value", KafkaClusterService.json("{\"compatibilityLevel\":\"BACKWARD\"}")
        ));
        KafkaClusterService service = new KafkaClusterService(admin, client, configuration, writeConfig(false, false));

        var overview = service.schemaRegistry();
        var detail = service.schemaRegistrySubject("orders-value", 2);

        assertNull(overview.error());
        assertTrue(overview.data().configured());
        assertEquals(List.of(1, 2), overview.data().subjects().get(0).versions());
        assertNull(detail.error());
        assertEquals(7, detail.data().id());
        assertEquals("BACKWARD", detail.data().compatibility());
        assertEquals("money-value", detail.data().references().get(0).subject());
    }

    @Test
    void schemaRegistryWritesUseWriteAndDestructiveGates() {
        AdminClient admin = mock(AdminClient.class);
        KafkaIntegrationConfiguration configuration = integrationConfig("http://schema-registry", null, null);
        FakeIntegrationClient client = new FakeIntegrationClient(Map.of(
            "Schema Registry POST /subjects/orders-value/versions", KafkaClusterService.json("{\"id\":9}"),
            "Schema Registry DELETE /subjects/orders-value", KafkaClusterService.json("[1,2]")
        ));

        var disabled = new KafkaClusterService(admin, client, configuration, writeConfig(false, false))
            .registerSchema(new KafkaClusterResponse.RegisterSchemaRequest("orders-value", "{}", "JSON", false, "REGISTER SCHEMA orders-value"));
        var destructiveDisabled = new KafkaClusterService(admin, client, configuration, writeConfig(true, false))
            .deleteSchemaSubject(new KafkaClusterResponse.DeleteSchemaSubjectRequest("orders-value", false, "DELETE SCHEMA SUBJECT orders-value"));
        var applied = new KafkaClusterService(admin, client, configuration, writeConfig(true, true))
            .registerSchema(new KafkaClusterResponse.RegisterSchemaRequest("orders-value", "{}", "JSON", false, "REGISTER SCHEMA orders-value"));

        assertEquals("Kafka writes are disabled. Set micronaut.control-panel.kafka.writes.enabled=true to enable write endpoints.", disabled.error());
        assertEquals("Kafka destructive actions are disabled. Set micronaut.control-panel.kafka.writes.destructive-enabled=true to enable this action.", destructiveDisabled.error());
        assertNull(applied.error());
        assertTrue(applied.data().applied());
        assertEquals("Schema Registry POST /subjects/orders-value/versions", client.lastAction());
    }

    @Test
    void kafkaConnectReadFiltersSensitiveConfigAndWritesAreGated() {
        AdminClient admin = mock(AdminClient.class);
        KafkaIntegrationConfiguration configuration = integrationConfig(null, "http://connect", null);
        FakeIntegrationClient client = new FakeIntegrationClient(Map.of(
            "Kafka Connect GET /connectors", KafkaClusterService.json("[\"jdbc-sink\"]"),
            "Kafka Connect GET /connectors/jdbc-sink/status", KafkaClusterService.json("""
                {"name":"jdbc-sink","type":"sink","connector":{"state":"RUNNING","worker_id":"worker:8083"},"tasks":[{"id":0,"state":"RUNNING","worker_id":"worker:8083"}]}
                """),
            "Kafka Connect GET /connectors/jdbc-sink/config", KafkaClusterService.json("{\"connector.class\":\"JdbcSinkConnector\",\"connection.password\":\"secret\"}"),
            "Kafka Connect PUT /connectors/jdbc-sink/pause", JsonNode.nullNode()
        ));
        KafkaClusterService service = new KafkaClusterService(admin, client, configuration, writeConfig(true, false));

        var overview = service.kafkaConnect();
        var pause = service.pauseConnector(new KafkaClusterResponse.ConnectorActionRequest("jdbc-sink", false, "PAUSE CONNECTOR jdbc-sink"));

        assertNull(overview.error());
        assertEquals("RUNNING", overview.data().connectors().get(0).state());
        assertTrue(overview.data().connectors().get(0).config().containsKey("connector.class"));
        assertFalse(overview.data().connectors().get(0).config().containsKey("connection.password"));
        assertNull(pause.error());
        assertTrue(pause.data().applied());
    }

    @Test
    void ksqlDbReadViewsLoadStreamsTablesAndQueries() {
        AdminClient admin = mock(AdminClient.class);
        KafkaIntegrationConfiguration configuration = integrationConfig(null, null, "http://ksqldb");
        FakeIntegrationClient client = new FakeIntegrationClient(Map.of(
            "ksqlDB POST /ksql {ksql=SHOW STREAMS;}", KafkaClusterService.json("[{\"streams\":[{\"name\":\"ORDERS\",\"topic\":\"orders\",\"password\":\"hidden\"}]}]"),
            "ksqlDB POST /ksql {ksql=SHOW TABLES;}", KafkaClusterService.json("[{\"tables\":[{\"name\":\"CUSTOMERS\"}]}]"),
            "ksqlDB POST /ksql {ksql=SHOW QUERIES;}", KafkaClusterService.json("[{\"queries\":[{\"id\":\"CSAS_1\",\"state\":\"RUNNING\"}]}]")
        ));
        KafkaClusterService service = new KafkaClusterService(admin, client, configuration, writeConfig(false, false));

        var section = service.ksqldb();

        assertNull(section.error());
        assertEquals("ORDERS", section.data().streams().get(0).get("name"));
        assertFalse(section.data().streams().get(0).containsKey("password"));
        assertEquals("CUSTOMERS", section.data().tables().get(0).get("name"));
        assertEquals("CSAS_1", section.data().queries().get(0).get("id"));
    }

    @Test
    void controllerKeepsAllEndpointsUnderKafkaSecurityPath() {
        assertEquals(ControlPanelSecurityPaths.KAFKA, KafkaClusterController.class.getAnnotation(Controller.class).value());
        Set<String> postMethods = Set.of(
            "createTopic",
            "updateTopicConfig",
            "increasePartitions",
            "deleteTopic",
            "produceMessage",
            "deleteConsumerGroup",
            "resetConsumerGroupOffsets",
            "pauseAppConsumer",
            "resumeAppConsumer",
            "registerSchema",
            "updateSchemaCompatibility",
            "deleteSchemaSubject",
            "deleteSchemaVersion",
            "pauseConnector",
            "resumeConnector",
            "restartConnector",
            "restartConnectorTask",
            "updateConnectorConfig",
            "deleteConnector"
        );
        for (Method method : KafkaClusterController.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(Put.class), method.getName());
            assertFalse(method.isAnnotationPresent(Patch.class), method.getName());
            assertFalse(method.isAnnotationPresent(Delete.class), method.getName());
            if (method.isAnnotationPresent(Get.class) || method.isAnnotationPresent(Post.class)) {
                assertTrue(Modifier.isPublic(method.getModifiers()), method.getName());
            }
            if (postMethods.contains(method.getName())) {
                assertTrue(method.isAnnotationPresent(Post.class), method.getName());
            }
        }
    }

    @Test
    void controllerEndpointsDelegateToServiceSections() {
        KafkaClusterController controller = new KafkaClusterController(new KafkaClusterService(mock(AdminClient.class)));

        assertNotNull(controller.summary());
        assertNotNull(controller.brokers());
        assertNotNull(controller.topics(null, false, 0, 25));
        assertNotNull(controller.topic("orders"));
        assertNotNull(controller.consumerGroups());
        assertNotNull(controller.consumerGroup("orders-group"));
        assertNotNull(controller.appConsumers());
        assertNotNull(controller.messages("orders", 0, "beginning", null, null, 25));
        assertNotNull(controller.writes());
        assertNotNull(controller.schemaRegistry());
        assertNotNull(controller.schemaRegistrySubject("orders-value", 1));
        assertNotNull(controller.kafkaConnect());
        assertNotNull(controller.ksqldb());
        assertNotNull(controller.createTopic(new KafkaClusterResponse.CreateTopicRequest("orders", 1, (short) 1, Map.of(), true, null)));
        assertNotNull(controller.updateTopicConfig(new KafkaClusterResponse.UpdateTopicConfigRequest("orders", Map.of("cleanup.policy", "delete"), true, null)));
        assertNotNull(controller.increasePartitions(new KafkaClusterResponse.IncreasePartitionsRequest("orders", 2, true, null)));
        assertNotNull(controller.deleteTopic(new KafkaClusterResponse.DeleteTopicRequest("orders", true, null)));
        assertNotNull(controller.produceMessage(produceRequest("orders", null, "ok", List.of())));
        assertNotNull(controller.deleteConsumerGroup(new KafkaClusterResponse.DeleteConsumerGroupRequest("orders-group", true, null)));
        assertNotNull(controller.resetConsumerGroupOffsets(new KafkaClusterResponse.ResetOffsetsRequest("orders-group", "earliest", null, null, null, null, true, null)));
        assertNotNull(controller.pauseAppConsumer(new KafkaClusterResponse.AppConsumerActionRequest("listener", List.of(), true, null)));
        assertNotNull(controller.resumeAppConsumer(new KafkaClusterResponse.AppConsumerActionRequest("listener", List.of(), true, null)));
        assertNotNull(controller.registerSchema(new KafkaClusterResponse.RegisterSchemaRequest("orders-value", "{}", null, true, null)));
        assertNotNull(controller.updateSchemaCompatibility(new KafkaClusterResponse.UpdateSchemaCompatibilityRequest("orders-value", "BACKWARD", true, null)));
        assertNotNull(controller.deleteSchemaSubject(new KafkaClusterResponse.DeleteSchemaSubjectRequest("orders-value", true, null)));
        assertNotNull(controller.deleteSchemaVersion(new KafkaClusterResponse.DeleteSchemaVersionRequest("orders-value", 1, true, null)));
        assertNotNull(controller.pauseConnector(new KafkaClusterResponse.ConnectorActionRequest("jdbc-sink", true, null)));
        assertNotNull(controller.resumeConnector(new KafkaClusterResponse.ConnectorActionRequest("jdbc-sink", true, null)));
        assertNotNull(controller.restartConnector(new KafkaClusterResponse.ConnectorActionRequest("jdbc-sink", true, null)));
        assertNotNull(controller.restartConnectorTask(new KafkaClusterResponse.ConnectorTaskActionRequest("jdbc-sink", 0, true, null)));
        assertNotNull(controller.updateConnectorConfig(new KafkaClusterResponse.UpdateConnectorConfigRequest("jdbc-sink", Map.of("connector.class", "JdbcSinkConnector"), true, null)));
        assertNotNull(controller.deleteConnector(new KafkaClusterResponse.ConnectorActionRequest("jdbc-sink", true, null)));
    }

    @Test
    void controlPanelMetadataUsesKafkaClusterContract() {
        KafkaClusterControlPanel panel = new KafkaClusterControlPanel(new ControlPanelConfiguration(KafkaClusterControlPanel.NAME));

        assertEquals(KafkaClusterControlPanel.NAME, panel.getName());
        assertEquals("Kafka Cluster", panel.getTitle());
        assertEquals(KafkaStreamsControlPanel.CATEGORY, panel.getCategory());
        assertEquals("read-only", panel.getBadge());
        assertEquals(KafkaClusterController.PATH, panel.getBody().controllerPath());
    }

    @Test
    void writeConfigurationExposesDefaultLimitsAndActionOverrides() {
        KafkaClusterWriteConfiguration configuration = new KafkaClusterWriteConfiguration();
        KafkaClusterWriteConfiguration.Actions actions = new KafkaClusterWriteConfiguration.Actions();

        configuration.setEnabled(true);
        configuration.setDestructiveEnabled(true);
        configuration.setMaxMessageValueBytes(10);
        configuration.setMaxMessageKeyBytes(11);
        configuration.setMaxMessageHeaders(12);
        configuration.setMaxMessageHeaderKeyBytes(13);
        configuration.setMaxMessageHeaderValueBytes(14);
        actions.setCreateTopic(false);
        actions.setUpdateTopicConfig(false);
        actions.setIncreasePartitions(false);
        actions.setDeleteTopic(false);
        actions.setProduceMessage(false);
        actions.setDeleteConsumerGroup(false);
        actions.setResetConsumerGroupOffsets(false);
        actions.setPauseAppConsumer(false);
        actions.setResumeAppConsumer(false);
        actions.setRegisterSchema(false);
        actions.setUpdateSchemaCompatibility(false);
        actions.setDeleteSchemaSubject(false);
        actions.setDeleteSchemaVersion(false);
        actions.setPauseConnector(false);
        actions.setResumeConnector(false);
        actions.setRestartConnector(false);
        actions.setRestartConnectorTask(false);
        actions.setUpdateConnectorConfig(false);
        actions.setDeleteConnector(false);
        configuration.setActions(actions);

        assertTrue(configuration.isEnabled());
        assertTrue(configuration.isDestructiveEnabled());
        assertEquals(10, configuration.getMaxMessageValueBytes());
        assertEquals(11, configuration.getMaxMessageKeyBytes());
        assertEquals(12, configuration.getMaxMessageHeaders());
        assertEquals(13, configuration.getMaxMessageHeaderKeyBytes());
        assertEquals(14, configuration.getMaxMessageHeaderValueBytes());
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_CREATE_TOPIC));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_UPDATE_TOPIC_CONFIG));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_INCREASE_PARTITIONS));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_DELETE_TOPIC));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_PRODUCE_MESSAGE));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_DELETE_CONSUMER_GROUP));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_RESET_CONSUMER_GROUP_OFFSETS));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_PAUSE_APP_CONSUMER));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_RESUME_APP_CONSUMER));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_REGISTER_SCHEMA));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_UPDATE_SCHEMA_COMPATIBILITY));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_DELETE_SCHEMA_SUBJECT));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_DELETE_SCHEMA_VERSION));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_PAUSE_CONNECTOR));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_RESUME_CONNECTOR));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_RESTART_CONNECTOR));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_RESTART_CONNECTOR_TASK));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_UPDATE_CONNECTOR_CONFIG));
        assertFalse(configuration.actionEnabled(KafkaClusterService.ACTION_DELETE_CONNECTOR));
        assertFalse(configuration.actionEnabled("unknown"));
        assertEquals(actions, configuration.getActions());
    }

    @Test
    void defaultIntegrationClientBuildsRequestsAndHandlesResponses() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/subjects", exchange -> {
            requestPath.set(exchange.getRequestURI().getRawPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"ok\":true}");
        });
        server.createContext("/api/empty", exchange -> respond(exchange, 204, ""));
        server.createContext("/api/error", exchange -> respond(exchange, 500, "boom"));
        server.start();
        try {
            KafkaIntegrationConfiguration configuration = integrationConfig("http://127.0.0.1:" + server.getAddress().getPort() + "/api/", null, null);
            DefaultKafkaIntegrationClient client = new DefaultKafkaIntegrationClient(configuration, JsonMapper.createDefault());

            JsonNode response = client.request(
                KafkaClusterService.INTEGRATION_SCHEMA_REGISTRY,
                "POST",
                "subjects/" + KafkaIntegrationClient.encodePath("orders value"),
                Map.of("schema", "{}")
            );
            JsonNode empty = client.request(KafkaClusterService.INTEGRATION_SCHEMA_REGISTRY, "GET", "/empty", null);
            IOException error = assertThrows(IOException.class, () ->
                client.request(KafkaClusterService.INTEGRATION_SCHEMA_REGISTRY, "GET", "/error", null));

            assertEquals("/api/subjects/orders%20value", requestPath.get());
            assertEquals("{\"schema\":\"{}\"}", requestBody.get());
            assertEquals("true", response.get("ok").coerceStringValue());
            assertTrue(empty.isNull());
            assertTrue(error.getMessage().contains("HTTP 500"));
            assertEquals("a%20b", KafkaIntegrationClient.encodePath("a b"));
        } finally {
            server.stop(0);
        }
    }

    @SafeVarargs
    private static ConsumerRecords<byte[], byte[]> records(
        TopicPartition partition,
        ConsumerRecord<byte[], byte[]>... records) {
        return new ConsumerRecords<>(Map.of(partition, List.of(records)));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    private static ConsumerRecord<byte[], byte[]> record(long offset, byte[] value, byte @Nullable [] key) {
        return new ConsumerRecord<>(
            "orders",
            0,
            offset,
            1234L,
            TimestampType.CREATE_TIME,
            key == null ? ConsumerRecord.NULL_SIZE : key.length,
            value == null ? ConsumerRecord.NULL_SIZE : value.length,
            key,
            value,
            new RecordHeaders().add("trace", "abc".getBytes(StandardCharsets.UTF_8)),
            Optional.empty()
        );
    }

    private static KafkaClusterWriteConfiguration writeConfig(boolean enabled, boolean destructiveEnabled) {
        KafkaClusterWriteConfiguration configuration = new KafkaClusterWriteConfiguration();
        configuration.setEnabled(enabled);
        configuration.setDestructiveEnabled(destructiveEnabled);
        return configuration;
    }

    private static KafkaClusterService serviceWithProduceLimits() {
        KafkaClusterWriteConfiguration configuration = writeConfig(true, false);
        configuration.setMaxMessageValueBytes(4);
        configuration.setMaxMessageKeyBytes(3);
        configuration.setMaxMessageHeaders(2);
        configuration.setMaxMessageHeaderKeyBytes(3);
        configuration.setMaxMessageHeaderValueBytes(3);
        return new KafkaClusterService(mock(AdminClient.class), null, null, null, configuration);
    }

    private static KafkaClusterResponse.ProduceMessageRequest produceRequest(String topic,
                                                                            @Nullable String key,
                                                                            String value,
                                                                            List<KafkaClusterResponse.MessageHeaderInput> headers) {
        return new KafkaClusterResponse.ProduceMessageRequest(
            topic,
            null,
            key,
            value,
            "string",
            headers,
            false,
            "PRODUCE " + topic
        );
    }

    private static KafkaIntegrationConfiguration integrationConfig(@Nullable String schemaRegistryUrl,
                                                                  @Nullable String connectUrl,
                                                                  @Nullable String ksqlDbUrl) {
        KafkaIntegrationConfiguration configuration = new KafkaIntegrationConfiguration();
        configuration.getSchemaRegistry().setUrl(schemaRegistryUrl);
        configuration.getConnect().setUrl(connectUrl);
        configuration.getKsqldb().setUrl(ksqlDbUrl);
        return configuration;
    }

    private static final class FakeIntegrationClient implements KafkaIntegrationClient {
        private final Map<String, JsonNode> responses;
        private @Nullable String lastAction;

        FakeIntegrationClient(Map<String, JsonNode> responses) {
            this.responses = responses;
        }

        @Override
        public JsonNode request(String integration, String method, String path, @Nullable Object body) {
            String action = body == null
                ? integration + " " + method + " " + path
                : integration + " " + method + " " + path + " " + body;
            JsonNode response = responses.get(action);
            if (response == null) {
                action = integration + " " + method + " " + path;
                response = responses.get(action);
            }
            lastAction = action;
            if (response == null) {
                throw new IllegalStateException("No fake response for " + action);
            }
            return response;
        }

        @Nullable
        String lastAction() {
            return lastAction;
        }
    }

    private static void mockCluster(AdminClient admin) {
        DescribeClusterResult result = mock(DescribeClusterResult.class);
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(result);
        when(result.nodes()).thenReturn(KafkaFuture.completedFuture(List.of(BROKER_0, BROKER_1)));
        when(result.controller()).thenReturn(KafkaFuture.completedFuture(BROKER_0));
        when(result.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster-a"));
    }

    private static void mockTopics(AdminClient admin, Map<String, TopicDescription> topics) {
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(listTopicsResult);
        when(listTopicsResult.listings()).thenReturn(KafkaFuture.completedFuture(topics.values().stream()
            .map(topic -> new TopicListing(topic.name(), Uuid.randomUuid(), topic.isInternal()))
            .toList()));

        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(TopicCollection.class), any(DescribeTopicsOptions.class))).thenReturn(describeTopicsResult);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(topics));
    }

    private static void mockTopicDetail(AdminClient admin, TopicDescription topic) {
        mockTopicDetail(admin, Map.of(topic.name(), topic));
    }

    private static void mockTopicDetail(AdminClient admin, Map<String, TopicDescription> topics) {
        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(TopicCollection.class), any(DescribeTopicsOptions.class))).thenReturn(describeTopicsResult);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(topics));
    }

    private static void mockConsumerGroups(AdminClient admin, int count) {
        ListGroupsResult result = mock(ListGroupsResult.class);
        when(admin.listGroups(any(ListGroupsOptions.class))).thenReturn(result);
        Collection<GroupListing> groups = java.util.stream.IntStream.range(0, count)
            .mapToObj(index -> new GroupListing("group-" + index, Optional.empty(), "", Optional.empty()))
            .toList();
        KafkaFuture<Collection<GroupListing>> future = KafkaFuture.completedFuture(groups);
        when(result.all()).thenReturn(future);
    }

    private static void mockConsumerGroupList(AdminClient admin, String... groupIds) {
        ListGroupsResult result = mock(ListGroupsResult.class);
        when(admin.listGroups(any(ListGroupsOptions.class))).thenReturn(result);
        Collection<GroupListing> groups = java.util.Arrays.stream(groupIds)
            .map(groupId -> new GroupListing(groupId, Optional.empty(), "", Optional.empty()))
            .toList();
        when(result.all()).thenReturn(KafkaFuture.completedFuture(groups));
    }

    private static void mockConsumerGroupDescriptions(
        AdminClient admin,
        Map<String, ConsumerGroupDescription> descriptions) {
        DescribeConsumerGroupsResult result = mock(DescribeConsumerGroupsResult.class);
        when(admin.describeConsumerGroups(
            any(Collection.class),
            any(DescribeConsumerGroupsOptions.class))
        ).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(descriptions));
    }

    private static void mockConsumerGroupOffsets(
        AdminClient admin,
        Map<String, Map<TopicPartition, OffsetAndMetadata>> offsets) {
        ListConsumerGroupOffsetsResult result = mock(ListConsumerGroupOffsetsResult.class);
        when(admin.listConsumerGroupOffsets(
            any(Map.class),
            any(ListConsumerGroupOffsetsOptions.class))
        ).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(offsets));
    }

    private static void mockConfigs(AdminClient admin, Map<ConfigResource, Config> configs) {
        DescribeConfigsResult result = mock(DescribeConfigsResult.class);
        when(admin.describeConfigs(any(Collection.class), any(DescribeConfigsOptions.class))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(configs));
    }

    private static void mockOffsets(AdminClient admin,
                                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> beginning,
                                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> end) {
        ListOffsetsResult beginningResult = mock(ListOffsetsResult.class);
        ListOffsetsResult endResult = mock(ListOffsetsResult.class);
        when(beginningResult.all()).thenReturn(KafkaFuture.completedFuture(beginning));
        when(endResult.all()).thenReturn(KafkaFuture.completedFuture(end));
        when(admin.listOffsets(anyMap(), any(ListOffsetsOptions.class))).thenReturn(beginningResult, endResult);
    }

    private static void mockOffsets(AdminClient admin,
                                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> offsets) {
        ListOffsetsResult result = mock(ListOffsetsResult.class);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(offsets));
        when(admin.listOffsets(anyMap(), any(ListOffsetsOptions.class))).thenReturn(result);
    }

    private static ConsumerGroupDescription consumerGroup(
        String groupId,
        GroupState state,
        String protocol,
        MemberDescription... members) {
        return new ConsumerGroupDescription(
            groupId,
            false,
            List.of(members),
            protocol,
            GroupType.CONSUMER,
            state,
            BROKER_0,
            Set.of(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static MemberDescription member(
        String consumerId,
        String clientId,
        String host,
        TopicPartition... topicPartitions) {
        return new MemberDescription(
            consumerId,
            Optional.empty(),
            Optional.empty(),
            clientId,
            host,
            new MemberAssignment(Set.of(topicPartitions)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static TopicDescription topic(String name, boolean internal, TopicPartitionInfo... partitions) {
        return new TopicDescription(name, internal, List.of(partitions), Set.of());
    }

    private static TopicPartitionInfo partition(int id, Node leader, List<Node> replicas, List<Node> isr) {
        return new TopicPartitionInfo(id, leader, replicas, isr);
    }

    private static Config config(ConfigEntry... entries) {
        return new Config(List.of(entries));
    }

    private static ConfigEntry entry(String name, String value) {
        return new ConfigEntry(name, value);
    }

    private static ConfigEntry sensitiveEntry(String name, String value) {
        return new ConfigEntry(
            name,
            value,
            ConfigEntry.ConfigSource.DYNAMIC_DEFAULT_BROKER_CONFIG,
            true,
            false,
            List.of(),
            ConfigEntry.ConfigType.STRING,
            null
        );
    }

    private static ConfigResource brokerResource(int id) {
        return new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(id));
    }

    private static ConfigResource topicResource(String topic) {
        return new ConfigResource(ConfigResource.Type.TOPIC, topic);
    }
}
