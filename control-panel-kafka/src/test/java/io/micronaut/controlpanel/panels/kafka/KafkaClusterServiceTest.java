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

import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Controller;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
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
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.GroupType;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
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
    void controllerIntroducesOnlyGetEndpoints() {
        assertEquals(ControlPanelSecurityPaths.KAFKA, KafkaClusterController.class.getAnnotation(Controller.class).value());
        for (Method method : KafkaClusterController.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(Post.class), method.getName());
            assertFalse(method.isAnnotationPresent(Put.class), method.getName());
            assertFalse(method.isAnnotationPresent(Patch.class), method.getName());
            assertFalse(method.isAnnotationPresent(Delete.class), method.getName());
            if (method.isAnnotationPresent(Get.class)) {
                assertTrue(Modifier.isPublic(method.getModifiers()), method.getName());
            }
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
