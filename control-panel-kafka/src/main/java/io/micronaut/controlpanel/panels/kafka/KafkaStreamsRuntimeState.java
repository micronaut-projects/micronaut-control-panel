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
import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Runtime state exposed by Micronaut Health for a Kafka Streams application.
 *
 * @param available whether runtime details are visible through Health
 * @param status health status for the Kafka Streams application
 * @param message optional runtime-state message
 * @param threads stream thread runtime details
 * @param hasThreads whether stream thread details are present
 */
@Internal
@ReflectiveAccess
public record KafkaStreamsRuntimeState(boolean available,
                                       String status,
                                       String message,
                                       List<ThreadState> threads,
                                       boolean hasThreads) {

    private static final String STATUS_UNKNOWN = "UNKNOWN";

    static KafkaStreamsRuntimeState unavailable(String message) {
        return new KafkaStreamsRuntimeState(false, STATUS_UNKNOWN, message, List.of(), false);
    }

    static KafkaStreamsRuntimeState available(String status, String message, List<ThreadState> threads) {
        List<ThreadState> threadStates = List.copyOf(threads);
        return new KafkaStreamsRuntimeState(true, emptyToUnknown(status), message, threadStates, !threadStates.isEmpty());
    }

    private static String emptyToUnknown(String status) {
        return status == null || status.isBlank() ? STATUS_UNKNOWN : status;
    }

    /**
     * Runtime state for one Kafka Streams thread.
     *
     * @param name stream thread name
     * @param state stream thread state
     * @param adminClientId admin client identifier
     * @param consumerClientId consumer client identifier
     * @param restoreConsumerClientId restore consumer client identifier
     * @param producerClientIds producer client identifiers
     * @param hasProducerClientIds whether producer client identifiers are present
     * @param activeTasks active task summary
     * @param standbyTasks standby task summary
     */
    @ReflectiveAccess
    public record ThreadState(String name,
                              String state,
                              String adminClientId,
                              String consumerClientId,
                              String restoreConsumerClientId,
                              List<String> producerClientIds,
                              boolean hasProducerClientIds,
                              TaskSummary activeTasks,
                              TaskSummary standbyTasks) {

        public ThreadState {
            producerClientIds = producerClientIds == null ? List.of() : List.copyOf(producerClientIds);
            hasProducerClientIds = !producerClientIds.isEmpty();
            activeTasks = activeTasks == null ? TaskSummary.empty() : activeTasks;
            standbyTasks = standbyTasks == null ? TaskSummary.empty() : standbyTasks;
        }

        ThreadState(String name,
                    String state,
                    String adminClientId,
                    String consumerClientId,
                    String restoreConsumerClientId,
                    List<String> producerClientIds,
                    TaskSummary activeTasks,
                    TaskSummary standbyTasks) {
            this(name, state, adminClientId, consumerClientId, restoreConsumerClientId, producerClientIds, false, activeTasks, standbyTasks);
        }
    }

    /**
     * Summary of a Kafka Streams task set.
     *
     * @param taskId task identifier
     * @param partitions task partitions reported by Health
     * @param available whether task details are present
     * @param partitionCount number of reported partitions
     */
    @ReflectiveAccess
    public record TaskSummary(String taskId,
                              List<String> partitions,
                              boolean available,
                              int partitionCount) {

        public TaskSummary {
            partitions = partitions == null ? List.of() : List.copyOf(partitions);
            available = taskId != null || !partitions.isEmpty();
            partitionCount = partitions.size();
        }

        TaskSummary(String taskId, List<String> partitions) {
            this(taskId, partitions, false, 0);
        }

        static TaskSummary empty() {
            return new TaskSummary(null, List.of());
        }
    }
}
