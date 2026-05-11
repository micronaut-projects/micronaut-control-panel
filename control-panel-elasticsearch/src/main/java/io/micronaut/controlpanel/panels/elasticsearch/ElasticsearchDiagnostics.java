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
package io.micronaut.controlpanel.panels.elasticsearch;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * View model for the Elasticsearch panel.
 *
 * @param state panel state
 * @param message safe user-facing message
 * @param connection sanitized connection context
 * @param cluster cluster identity
 * @param health cluster health and shard summary
 * @param indices visible index summaries
 * @param warnings partial-data warnings
 * @param indexCount total visible index count
 * @param displayedIndexCount displayed index count after limits
 * @param indicesTruncated whether the index list was truncated
 * @param mappingFieldsTruncated whether the mapping preview was truncated
 */
@ReflectiveAccess
public record ElasticsearchDiagnostics(State state,
                                       String message,
                                       ConnectionContext connection,
                                       ClusterInfo cluster,
                                       HealthSummary health,
                                       List<IndexSummary> indices,
                                       List<String> warnings,
                                       int indexCount,
                                       int displayedIndexCount,
                                       boolean indicesTruncated,
                                       boolean mappingFieldsTruncated) {

    public ElasticsearchDiagnostics {
        connection = connection == null ? ConnectionContext.empty() : connection;
        cluster = cluster == null ? ClusterInfo.empty() : cluster;
        health = health == null ? HealthSummary.empty() : health;
        indices = indices == null ? List.of() : List.copyOf(indices);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    static ElasticsearchDiagnostics unavailable(ConnectionContext connection, State state, String message) {
        return new ElasticsearchDiagnostics(state, message, connection, ClusterInfo.empty(), HealthSummary.empty(), List.of(), List.of(), 0, 0, false, false);
    }

    public boolean available() {
        return state == State.AVAILABLE || state == State.PARTIAL || state == State.NO_VISIBLE_INDICES;
    }

    public boolean hasIndices() {
        return !indices.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public String stateLabel() {
        return state.displayName();
    }

    public String badgeClass() {
        return state.badgeClass();
    }

    /**
     * Panel state.
     */
    @ReflectiveAccess
    public enum State {
        AVAILABLE("Available", "badge-primary"),
        NO_CLIENT("No client", "badge-secondary"),
        UNAVAILABLE("Unavailable", "badge-destructive"),
        AUTHENTICATION_FAILED("Authentication failed", "badge-destructive"),
        AUTHORIZATION_FAILED("Authorization failed", "badge-destructive"),
        UNSUPPORTED("Unsupported response", "badge-secondary"),
        DELAYED("Delayed", "badge-secondary"),
        PARTIAL("Partial", "badge-secondary"),
        NO_VISIBLE_INDICES("No visible indices", "badge-secondary"),
        ERROR("Error", "badge-destructive");

        private final String displayName;
        private final String badgeClass;

        State(String displayName, String badgeClass) {
            this.displayName = displayName;
            this.badgeClass = badgeClass;
        }

        public String displayName() {
            return displayName;
        }

        public String badgeClass() {
            return badgeClass;
        }
    }

    /**
     * Sanitized Elasticsearch connection context.
     *
     * @param clientBean client bean name
     * @param hosts configured hosts without secrets
     * @param hasHosts whether any hosts are known
     */
    @ReflectiveAccess
    public record ConnectionContext(String clientBean, List<String> hosts, boolean hasHosts) {
        public ConnectionContext {
            clientBean = clientBean == null || clientBean.isBlank() ? "default" : clientBean;
            hosts = hosts == null ? List.of() : List.copyOf(hosts);
            hasHosts = !hosts.isEmpty();
        }

        static ConnectionContext empty() {
            return new ConnectionContext("default", List.of(), false);
        }
    }

    /**
     * Cluster identity.
     *
     * @param name cluster name
     * @param uuid cluster UUID
     * @param nodeName responding node name
     * @param version Elasticsearch version
     * @param tagline Elasticsearch tagline
     */
    @ReflectiveAccess
    public record ClusterInfo(String name, String uuid, String nodeName, String version, String tagline) {
        static ClusterInfo empty() {
            return new ClusterInfo("", "", "", "", "");
        }
    }

    /**
     * Cluster health and shard summary.
     *
     * @param status health status
     * @param statusBadgeClass badge class
     * @param timedOut whether the health call timed out
     * @param numberOfNodes node count
     * @param numberOfDataNodes data-node count
     * @param activeShards active shard count
     * @param activePrimaryShards active primary shard count
     * @param relocatingShards relocating shard count
     * @param initializingShards initializing shard count
     * @param unassignedShards unassigned shard count
     * @param delayedUnassignedShards delayed unassigned shard count
     * @param pendingTasks pending task count
     * @param activeShardsPercent active shard percentage
     */
    @ReflectiveAccess
    public record HealthSummary(String status,
                                String statusBadgeClass,
                                boolean timedOut,
                                int numberOfNodes,
                                int numberOfDataNodes,
                                int activeShards,
                                int activePrimaryShards,
                                int relocatingShards,
                                int initializingShards,
                                int unassignedShards,
                                int delayedUnassignedShards,
                                int pendingTasks,
                                String activeShardsPercent) {
        static HealthSummary empty() {
            return new HealthSummary("unknown", "badge-secondary", false, 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
        }
    }

    /**
     * Index diagnostics summary.
     *
     * @param name index name
     * @param health health status when visible
     * @param status open/close status when visible
     * @param primaryShards primary shard count
     * @param replicaShards replica shard count
     * @param documentCount document count
     * @param storeSize store size
     * @param aliases aliases visible for this index
     * @param aliasesTruncated whether aliases were truncated
     * @param mappingFieldCount number of mapping fields
     * @param mappingPreview bounded mapping preview
     */
    @ReflectiveAccess
    public record IndexSummary(String name,
                               String health,
                               String status,
                               String primaryShards,
                               String replicaShards,
                               String documentCount,
                               String storeSize,
                               List<String> aliases,
                               boolean aliasesTruncated,
                               int mappingFieldCount,
                               List<MappingField> mappingPreview) {
        public IndexSummary {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            mappingPreview = mappingPreview == null ? List.of() : List.copyOf(mappingPreview);
        }

        public boolean hasAliases() {
            return !aliases.isEmpty();
        }

        public boolean hasMappingPreview() {
            return !mappingPreview.isEmpty();
        }
    }

    /**
     * Mapping field summary.
     *
     * @param name field path
     * @param type Elasticsearch mapping type
     */
    @ReflectiveAccess
    public record MappingField(String name, String type) {
    }
}
