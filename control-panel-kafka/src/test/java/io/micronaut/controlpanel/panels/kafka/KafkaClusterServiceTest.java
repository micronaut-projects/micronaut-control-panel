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

import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConfigsOptions;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
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
    void controllerIntroducesOnlyGetEndpoints() {
        for (Method method : KafkaClusterController.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(Post.class), method.getName());
            assertFalse(method.isAnnotationPresent(Put.class), method.getName());
            assertFalse(method.isAnnotationPresent(Patch.class), method.getName());
            assertFalse(method.isAnnotationPresent(Delete.class), method.getName());
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
        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(TopicCollection.class), any(DescribeTopicsOptions.class))).thenReturn(describeTopicsResult);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(Map.of(topic.name(), topic)));
    }

    private static void mockConsumerGroups(AdminClient admin, int count) {
        ListConsumerGroupsResult result = mock(ListConsumerGroupsResult.class);
        when(admin.listConsumerGroups(any(ListConsumerGroupsOptions.class))).thenReturn(result);
        Collection<?> groups = java.util.Collections.nCopies(count, new Object());
        @SuppressWarnings({"rawtypes", "unchecked"})
        KafkaFuture<Collection<org.apache.kafka.clients.admin.ConsumerGroupListing>> future = (KafkaFuture) KafkaFuture.completedFuture(groups);
        when(result.all()).thenReturn(future);
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
