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
package io.micronaut.controlpanel.panels.opensearch;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The OpenSearch cluster of the application. Internal to the OpenSearch panel views.
 *
 * @param hosts the configured hosts, without user info
 * @param clientOpen whether the application has created an OpenSearch client
 * @param cluster the cluster name, version and health, or {@code null} if they were not requested or could not be read
 * @param indices the indices, sorted by name, without hidden and system indices, capped at
 * {@value OpenSearchInspector#MAX_INDICES}
 * @param totalIndices the number of indices before the cap
 * @param error why the cluster could not be read, without the server's message, or {@code null}
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record OpenSearchInfo(
    List<String> hosts,
    boolean clientOpen,
    @Nullable Cluster cluster,
    List<Index> indices,
    int totalIndices,
    @Nullable String error
) {

    /**
     * @return whether the index list was capped
     */
    public boolean truncated() {
        return totalIndices > indices.size();
    }

    private static String healthBadge(@Nullable String health) {
        return health != null && "red".equals(health.toLowerCase(Locale.ROOT)) ? "badge-destructive" : "badge-secondary";
    }

    /**
     * The cluster name, version and health.
     *
     * @param name the cluster name
     * @param version the OpenSearch version
     * @param status the cluster health status: {@code green}, {@code yellow} or {@code red}
     * @param nodes the number of nodes
     * @param activeShards the number of active shards
     * @param unassignedShards the number of unassigned shards
     */
    @ReflectiveAccess
    public record Cluster(String name, String version, String status, int nodes, int activeShards, int unassignedShards) {

        /**
         * @return the CSS class of the health badge
         */
        public String badgeClass() {
            return healthBadge(status);
        }
    }

    /**
     * An index.
     *
     * @param name the index name
     * @param health the index health: {@code green}, {@code yellow} or {@code red}
     * @param status the index status: {@code open} or {@code close}
     * @param documents the number of documents, as reported by OpenSearch
     * @param size the store size, as reported by OpenSearch, such as {@code 4.2kb}
     * @param primaries the number of primary shards
     * @param replicas the number of replicas
     */
    @ReflectiveAccess
    public record Index(String name, String health, String status, String documents, String size, String primaries, String replicas) {

        /**
         * @return the CSS class of the health badge
         */
        public String badgeClass() {
            return healthBadge(health);
        }
    }
}
