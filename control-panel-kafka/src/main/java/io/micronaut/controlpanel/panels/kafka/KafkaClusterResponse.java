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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * JSON DTOs for the read-only Kafka Cluster control panel.
 */
@Internal
@SuppressWarnings("MissingJavadocType")
public final class KafkaClusterResponse {

    private KafkaClusterResponse() {
    }

    @Introspected
    @ReflectiveAccess
    public record Section<T>(@Nullable T data, @Nullable String error) {
        static <T> Section<T> ok(T data) {
            return new Section<>(data, null);
        }

        static <T> Section<T> error(String error) {
            return new Section<>(null, error);
        }
    }

    @Introspected
    @ReflectiveAccess
    public record Overview(@Nullable String clusterId,
                           @Nullable BrokerNode controller,
                           int brokerCount,
                           int topicCount,
                           int partitionCount,
                           int consumerGroupCount,
                           Map<String, String> config) {
    }

    @Introspected
    @ReflectiveAccess
    public record Broker(int id,
                         String host,
                         int port,
                         @Nullable String rack,
                         boolean controller,
                         int leaderPartitions,
                         int replicaPartitions,
                         int underReplicatedPartitions,
                         Map<String, String> config) {
    }

    @Introspected
    @ReflectiveAccess
    public record BrokerNode(int id, String host, int port, @Nullable String rack) {
    }

    @Introspected
    @ReflectiveAccess
    public record TopicPage(int start,
                            int length,
                            int recordsTotal,
                            int recordsFiltered,
                            List<TopicSummary> topics) {
    }

    @Introspected
    @ReflectiveAccess
    public record TopicSummary(String name,
                               boolean internal,
                               int partitions,
                               int replicationFactor,
                               boolean underReplicated,
                               @Nullable String cleanupPolicy,
                               @Nullable String retentionMs,
                               @Nullable String retentionBytes,
                               @Nullable String segmentBytes,
                               Map<String, String> config) {
    }

    @Introspected
    @ReflectiveAccess
    public record TopicDetail(TopicSummary topic, List<PartitionDetail> partitions) {
    }

    @Introspected
    @ReflectiveAccess
    public record PartitionDetail(int partition,
                                  @Nullable BrokerNode leader,
                                  List<BrokerNode> replicas,
                                  List<BrokerNode> isr,
                                  @Nullable Long beginningOffset,
                                  @Nullable Long endOffset,
                                  @Nullable Long sizeEstimate) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConsumerGroupSummary(String groupId,
                                       String state,
                                       String protocol,
                                       int members,
                                       int assignedPartitions,
                                       int committedPartitions,
                                       @Nullable Long totalLag) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConsumerGroupDetail(ConsumerGroupSummary group,
                                      List<ConsumerGroupMember> members,
                                      List<ConsumerGroupPartition> partitions) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConsumerGroupMember(String consumerId,
                                      @Nullable String groupInstanceId,
                                      String clientId,
                                      String host,
                                      List<String> assignment) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConsumerGroupPartition(String topic,
                                         int partition,
                                         boolean assigned,
                                         @Nullable Long committedOffset,
                                         @Nullable Long endOffset,
                                         @Nullable Long lag) {
    }

    @Introspected
    @ReflectiveAccess
    public record AppConsumer(String id,
                              List<String> subscriptions,
                              List<AppConsumerAssignment> assignments,
                              boolean paused) {
    }

    @Introspected
    @ReflectiveAccess
    public record AppConsumerAssignment(String topic, int partition, boolean paused) {
    }

    @Introspected
    @ReflectiveAccess
    public record MessagePage(String topic,
                              int partition,
                              String mode,
                              int limit,
                              @Nullable Long startOffset,
                              @Nullable Long endOffset,
                              List<MessageRecord> records) {
    }

    @Introspected
    @ReflectiveAccess
    public record MessageRecord(long offset,
                                long timestamp,
                                String timestampType,
                                @Nullable RenderedPayload key,
                                @Nullable RenderedPayload value,
                                List<MessageHeader> headers,
                                int partition) {
    }

    @Introspected
    @ReflectiveAccess
    public record MessageHeader(String key, @Nullable RenderedPayload value) {
    }

    @Introspected
    @ReflectiveAccess
    public record RenderedPayload(String format,
                                  @Nullable String text,
                                  @Nullable String base64,
                                  int size,
                                  boolean truncated) {
    }

    @Introspected
    @ReflectiveAccess
    public record WriteCapabilities(boolean writesEnabled,
                                    boolean destructiveEnabled,
                                    Map<String, Boolean> actions) {
    }

    @Introspected
    @ReflectiveAccess
    public record SchemaRegistryOverview(boolean configured,
                                         @Nullable String url,
                                         List<SchemaSubject> subjects) {
    }

    @Introspected
    @ReflectiveAccess
    public record SchemaSubject(String subject, List<Integer> versions) {
    }

    @Introspected
    @ReflectiveAccess
    public record SchemaVersionDetail(String subject,
                                      int version,
                                      @Nullable Integer id,
                                      @Nullable String schemaType,
                                      @Nullable String schema,
                                      @Nullable String compatibility,
                                      List<SchemaReference> references) {
    }

    @Introspected
    @ReflectiveAccess
    public record SchemaReference(String name, String subject, int version) {
    }

    @Introspected
    @ReflectiveAccess
    public record KafkaConnectOverview(boolean configured,
                                       @Nullable String url,
                                       List<ConnectorSummary> connectors) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConnectorSummary(String name,
                                   @Nullable String type,
                                   @Nullable String state,
                                   @Nullable String workerId,
                                   List<ConnectorTask> tasks,
                                   Map<String, String> config) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConnectorTask(int id, @Nullable String state, @Nullable String workerId) {
    }

    @Introspected
    @ReflectiveAccess
    public record KsqlDbOverview(boolean configured,
                                 @Nullable String url,
                                 List<Map<String, String>> streams,
                                 List<Map<String, String>> tables,
                                 List<Map<String, String>> queries) {
    }

    @Introspected
    @ReflectiveAccess
    public record ActionResult(boolean applied,
                               String action,
                               String target,
                               String impact,
                               List<ActionError> errors) {
    }

    @Introspected
    @ReflectiveAccess
    public record ActionError(String target, String error) {
    }

    @Introspected
    @ReflectiveAccess
    public record CreateTopicRequest(String topic,
                                     int partitions,
                                     short replicationFactor,
                                     Map<String, String> config,
                                     boolean preview,
                                     @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record UpdateTopicConfigRequest(String topic,
                                           Map<String, @Nullable String> config,
                                           boolean preview,
                                           @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record IncreasePartitionsRequest(String topic,
                                            int totalPartitions,
                                            boolean preview,
                                            @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record DeleteTopicRequest(String topic,
                                     boolean preview,
                                     @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record ProduceMessageRequest(String topic,
                                        @Nullable Integer partition,
                                        @Nullable String key,
                                        String value,
                                        String format,
                                        List<MessageHeaderInput> headers,
                                        boolean preview,
                                        @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record MessageHeaderInput(String key, @Nullable String value) {
    }

    @Introspected
    @ReflectiveAccess
    public record DeleteConsumerGroupRequest(String groupId,
                                             boolean preview,
                                             @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record ResetOffsetsRequest(String groupId,
                                      String target,
                                      @Nullable String topic,
                                      @Nullable Integer partition,
                                      @Nullable Long offset,
                                      @Nullable Long timestamp,
                                      boolean preview,
                                      @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record AppConsumerActionRequest(String consumerId,
                                           List<TopicPartitionInput> partitions,
                                           boolean preview,
                                           @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record TopicPartitionInput(String topic, int partition) {
    }

    @Introspected
    @ReflectiveAccess
    public record RegisterSchemaRequest(String subject,
                                        String schema,
                                        @Nullable String schemaType,
                                        boolean preview,
                                        @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record UpdateSchemaCompatibilityRequest(String subject,
                                                   String compatibility,
                                                   boolean preview,
                                                   @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record DeleteSchemaSubjectRequest(String subject,
                                             boolean preview,
                                             @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record DeleteSchemaVersionRequest(String subject,
                                             int version,
                                             boolean preview,
                                             @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConnectorActionRequest(String connector,
                                         boolean preview,
                                         @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record ConnectorTaskActionRequest(String connector,
                                             int task,
                                             boolean preview,
                                             @Nullable String confirmation) {
    }

    @Introspected
    @ReflectiveAccess
    public record UpdateConnectorConfigRequest(String connector,
                                               Map<String, String> config,
                                               boolean preview,
                                               @Nullable String confirmation) {
    }
}
