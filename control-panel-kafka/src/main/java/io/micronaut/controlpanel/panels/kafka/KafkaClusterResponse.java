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
}
