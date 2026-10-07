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

import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.panels.opensearch.model.ClusterHealth;
import io.micronaut.controlpanel.panels.opensearch.model.ConnectionContext;
import io.micronaut.controlpanel.panels.opensearch.model.DiagnosticState;
import io.micronaut.controlpanel.panels.opensearch.model.IndexSummary;
import io.micronaut.controlpanel.panels.opensearch.model.MappingField;
import io.micronaut.controlpanel.panels.opensearch.model.OpenSearchDiagnostics;
import io.micronaut.core.annotation.Internal;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Collects read-only diagnostics from a configured OpenSearch client.
 */
@Internal
final class OpenSearchDiagnosticsService {

    static final int HARD_MAX_INDICES = 100;
    static final int HARD_MAX_MAPPING_FIELDS = 100;

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchDiagnosticsService.class);
    private static final String REST_CLIENT_HOSTS = "micronaut.opensearch.rest-client.http-hosts";
    private static final String HTTPCLIENT5_HOSTS = "micronaut.opensearch.httpclient5.http-hosts";
    private static final String AWS_ENDPOINT = "micronaut.opensearch.aws.endpoint";
    private static final String AWS_SIGNING_REGION = "micronaut.opensearch.aws.signing-region";

    private final String beanName;
    private final OpenSearchClient client;
    private final Environment environment;
    private final OpenSearchDiagnosticsConfiguration configuration;

    OpenSearchDiagnosticsService(String beanName,
                                 OpenSearchClient client,
                                 Environment environment,
                                 OpenSearchDiagnosticsConfiguration configuration) {
        this.beanName = beanName;
        this.client = client;
        this.environment = environment;
        this.configuration = configuration;
    }

    /**
     * Read current OpenSearch diagnostics.
     *
     * @return diagnostics suitable for rendering
     */
    public OpenSearchDiagnostics diagnostics() {
        ConnectionContext connection = connectionContext();
        try {
            ClusterHealth health = clusterHealth(client.cluster().health());
            IndexResult indexResult = indices();
            DiagnosticState state = health.red() ? DiagnosticState.RED : DiagnosticState.AVAILABLE;
            return OpenSearchDiagnostics.available(beanName, connection, state, health, indexResult.indices(), indexResult.partialMessage(), indexResult.truncated());
        } catch (OpenSearchException e) {
            LOG.debug("OpenSearch diagnostics failed for bean '{}': status={}", beanName, e.status(), e);
            return OpenSearchDiagnostics.failure(beanName, connection, classify(e), safeMessage(e));
        } catch (IOException e) {
            LOG.debug("OpenSearch diagnostics failed for bean '{}'", beanName, e);
            return OpenSearchDiagnostics.failure(beanName, connection, DiagnosticState.UNAVAILABLE, "Cluster unavailable or the client could not connect.");
        } catch (RuntimeException e) {
            LOG.debug("OpenSearch diagnostics failed for bean '{}'", beanName, e);
            return OpenSearchDiagnostics.failure(beanName, connection, DiagnosticState.ERROR, "OpenSearch diagnostics could not be collected.");
        }
    }

    private IndexResult indices() throws IOException {
        int maxIndices = bounded(configuration.getMaxIndices(), HARD_MAX_INDICES);
        int maxMappingFields = bounded(configuration.getMaxMappingFields(), HARD_MAX_MAPPING_FIELDS);
        List<IndicesRecord> records = client.cat().indices(i -> i.headers("health", "status", "index", "pri", "rep", "docs.count", "store.size"))
            .valueBody()
            .stream()
            .filter(indexRecord -> valuePresent(indexRecord.index()))
            .sorted(Comparator.comparing(IndicesRecord::index))
            .toList();

        List<String> selectedIndexNames = records.stream()
            .limit(maxIndices)
            .map(IndicesRecord::index)
            .toList();

        Map<String, List<String>> aliases = aliases(selectedIndexNames);
        Map<String, MappingSummary> mappings = mappings(selectedIndexNames, maxMappingFields);

        List<IndexSummary> summaries = records.stream()
            .limit(maxIndices)
            .map(indexRecord -> indexSummary(indexRecord, aliases, mappings))
            .toList();
        boolean truncated = records.size() > summaries.size();
        return new IndexResult(summaries, null, truncated);
    }

    private Map<String, List<String>> aliases(List<String> indexNames) throws IOException {
        if (indexNames.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        client.indices().getAlias(a -> a.index(indexNames).ignoreUnavailable(true).allowNoIndices(true))
            .result()
            .forEach((index, value) -> aliases.put(index, value.aliases().keySet().stream().sorted().toList()));
        return aliases;
    }

    private Map<String, MappingSummary> mappings(List<String> indexNames, int maxMappingFields) throws IOException {
        if (indexNames.isEmpty()) {
            return Map.of();
        }
        Map<String, MappingSummary> summaries = new LinkedHashMap<>();
        client.indices().getMapping(m -> m.index(indexNames).ignoreUnavailable(true).allowNoIndices(true))
            .result()
            .forEach((index, value) -> {
                TypeMapping mapping = value.mappings() == null ? value.item() : value.mappings();
                summaries.put(index, mappingSummary(mapping, maxMappingFields));
            });
        return summaries;
    }

    private static MappingSummary mappingSummary(TypeMapping mapping, int maxMappingFields) {
        if (mapping == null || mapping.properties().isEmpty()) {
            return MappingSummary.EMPTY;
        }
        List<MappingField> fields = new ArrayList<>();
        collectMappingFields("", mapping.properties(), fields, maxMappingFields + 1);
        boolean truncated = fields.size() > maxMappingFields;
        List<MappingField> visibleFields = truncated ? fields.subList(0, maxMappingFields) : fields;
        return new MappingSummary(List.copyOf(visibleFields), truncated);
    }

    private static void collectMappingFields(String prefix, Map<String, Property> properties, List<MappingField> fields, int maxMappingFields) {
        if (properties == null || properties.isEmpty() || fields.size() >= maxMappingFields) {
            return;
        }
        properties.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (fields.size() < maxMappingFields) {
                    String name = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
                    Property property = entry.getValue();
                    fields.add(new MappingField(name, property._kind().jsonValue()));
                    collectMappingFields(name, nestedProperties(property), fields, maxMappingFields);
                }
            });
    }

    private static Map<String, Property> nestedProperties(Property property) {
        if (property == null) {
            return Map.of();
        }
        if (property.isObject()) {
            return property.object().properties();
        }
        if (property.isNested()) {
            return property.nested().properties();
        }
        return Map.of();
    }

    private static IndexSummary indexSummary(IndicesRecord indexRecord, Map<String, List<String>> aliases, Map<String, MappingSummary> mappings) {
        MappingSummary mapping = mappings.getOrDefault(indexRecord.index(), MappingSummary.EMPTY);
        return new IndexSummary(
            value(indexRecord.index()),
            value(indexRecord.health()),
            value(indexRecord.status()),
            value(indexRecord.pri()),
            value(indexRecord.rep()),
            value(indexRecord.docsCount()),
            value(indexRecord.storeSize()),
            aliases.getOrDefault(indexRecord.index(), List.of()),
            mapping.fields(),
            mapping.truncated()
        );
    }

    private ClusterHealth clusterHealth(HealthResponse response) {
        return new ClusterHealth(
            value(response.clusterName()),
            response.status().name().toLowerCase(Locale.ENGLISH),
            response.timedOut(),
            response.numberOfNodes(),
            response.numberOfDataNodes(),
            response.numberOfPendingTasks(),
            response.numberOfInFlightFetch(),
            response.activeShardsPercentAsNumber(),
            response.activePrimaryShards(),
            response.activeShards(),
            response.relocatingShards(),
            response.initializingShards(),
            response.unassignedShards(),
            response.delayedUnassignedShards()
        );
    }

    private ConnectionContext connectionContext() {
        Set<String> hosts = new LinkedHashSet<>();
        hosts.addAll(sanitizedList(REST_CLIENT_HOSTS));
        hosts.addAll(sanitizedList(HTTPCLIENT5_HOSTS));
        String awsEndpoint = sanitizedEndpoint(environment.getProperty(AWS_ENDPOINT, String.class).orElse(null));
        String signingRegion = value(environment.getProperty(AWS_SIGNING_REGION, String.class).orElse(null));
        String transport = transport(hosts, awsEndpoint);
        return new ConnectionContext(transport, List.copyOf(hosts), awsEndpoint, signingRegion);
    }

    private List<String> sanitizedList(String property) {
        List<String> values = new ArrayList<>();
        environment.getProperty(property, String.class).ifPresent(value -> values.addAll(hostValues(value)));
        environment.getProperty(property, String[].class).ifPresent(array ->
            List.of(array).forEach(value -> values.addAll(hostValues(value)))
        );
        return values.stream()
            .map(OpenSearchDiagnosticsService::sanitizedEndpoint)
            .filter(OpenSearchDiagnosticsService::valuePresent)
            .distinct()
            .toList();
    }

    private static List<String> hostValues(String configured) {
        String value = value(configured);
        if (value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String host : value.split(",")) {
            String hostValue = value(host);
            if (valuePresent(hostValue)) {
                values.add(hostValue);
            }
        }
        return values;
    }

    static String sanitizedEndpoint(@Nullable String endpoint) {
        String value = value(endpoint);
        if (value.isBlank()) {
            return "";
        }
        int scheme = value.indexOf("://");
        int at = value.indexOf('@');
        if (scheme > -1 && at > scheme) {
            value = value.substring(0, scheme + 3) + "***@" + value.substring(at + 1);
        }
        int query = value.indexOf('?');
        if (query > -1) {
            value = value.substring(0, query);
        }
        return redactSecretLikeSegments(value);
    }

    private static String redactSecretLikeSegments(String value) {
        String[] parts = value.split("(?i)(access[_-]?key|secret[_-]?key|token|password|authorization|credential)=", -1);
        return parts.length == 1 ? value : parts[0] + "***";
    }

    private static DiagnosticState classify(OpenSearchException e) {
        return switch (e.status()) {
            case 401 -> DiagnosticState.AUTHENTICATION_FAILED;
            case 403 -> DiagnosticState.AUTHORIZATION_FAILED;
            case 400, 404 -> DiagnosticState.UNSUPPORTED;
            default -> DiagnosticState.ERROR;
        };
    }

    private static String safeMessage(OpenSearchException e) {
        ErrorCause error = e.error();
        String type = error == null ? "" : value(error.type());
        return switch (classify(e)) {
            case AUTHENTICATION_FAILED -> "Authentication failed while reading OpenSearch diagnostics.";
            case AUTHORIZATION_FAILED -> "Current credentials lack the cluster or index monitor privileges required for this panel.";
            case UNSUPPORTED -> type.isBlank() ? "OpenSearch returned an unsupported diagnostics response." : "OpenSearch returned an unsupported diagnostics response: " + type + ".";
            default -> "OpenSearch diagnostics could not be collected.";
        };
    }

    private static String transport(Set<String> hosts, String awsEndpoint) {
        if (valuePresent(awsEndpoint)) {
            return "Amazon OpenSearch Service";
        }
        if (!hosts.isEmpty()) {
            return "HTTP";
        }
        return "OpenSearch Java client";
    }

    private static int bounded(int configured, int hardMax) {
        return Math.clamp(configured, 1, hardMax);
    }

    private static String value(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean valuePresent(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private record IndexResult(List<IndexSummary> indices, @Nullable String partialMessage, boolean truncated) {
    }

    private record MappingSummary(List<MappingField> fields, boolean truncated) {
        static final MappingSummary EMPTY = new MappingSummary(List.of(), false);
    }
}
