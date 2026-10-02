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
package io.micronaut.controlpanel.panels.opensearch.model;

import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * OpenSearch cluster health details.
 *
 * @param clusterName cluster name
 * @param status cluster status
 * @param timedOut whether the health request timed out
 * @param numberOfNodes total node count
 * @param numberOfDataNodes data node count
 * @param numberOfPendingTasks pending task count
 * @param numberOfInFlightFetch in-flight fetch count
 * @param activeShardsPercent active shard percentage
 * @param activePrimaryShards active primary shard count
 * @param activeShards active shard count
 * @param relocatingShards relocating shard count
 * @param initializingShards initializing shard count
 * @param unassignedShards unassigned shard count
 * @param delayedUnassignedShards delayed unassigned shard count
 */
@ReflectiveAccess
public record ClusterHealth(String clusterName,
                            String status,
                            boolean timedOut,
                            int numberOfNodes,
                            int numberOfDataNodes,
                            int numberOfPendingTasks,
                            int numberOfInFlightFetch,
                            double activeShardsPercent,
                            int activePrimaryShards,
                            int activeShards,
                            int relocatingShards,
                            int initializingShards,
                            int unassignedShards,
                            int delayedUnassignedShards) {

    /**
     * @return true when OpenSearch reports red cluster health
     */
    public boolean red() {
        return "red".equalsIgnoreCase(status);
    }
}
