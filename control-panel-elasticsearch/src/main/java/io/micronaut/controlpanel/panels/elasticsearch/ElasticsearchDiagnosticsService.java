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

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.PropertyBase;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonpMappingException;
import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.elasticsearch.DefaultElasticsearchConfiguration;
import jakarta.inject.Singleton;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.ClusterInfo;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.ConnectionContext;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.HealthSummary;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.IndexSummary;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.MappingField;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State;

/**
 * Collects bounded, read-only Elasticsearch diagnostics for rendering.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@Internal
@Singleton
class ElasticsearchDiagnosticsService {

    private static final String DEFAULT_CLIENT_BEAN = "default";
    private static final String NO_CLIENT_MESSAGE = "No Elasticsearch client bean is available in this application context.";
    private static final String NO_VISIBLE_INDICES_MESSAGE = "The cluster responded, but no indices are visible to the current application credentials.";
    private static final String PARTIAL_MESSAGE = "The cluster responded, but some index metadata could not be read with the current credentials.";
    private static final Pattern SECRET_QUERY_PARAMETER = Pattern.compile("(?i)(authorization|api[-_]?key|password|secret|token)=([^&#;\\s]+)");

    private final BeanContext beanContext;
    private final Environment environment;
    private final ElasticsearchControlPanelConfiguration configuration;

    ElasticsearchDiagnosticsService(BeanContext beanContext,
                                    Environment environment,
                                    ElasticsearchControlPanelConfiguration configuration) {
        this.beanContext = beanContext;
        this.environment = environment;
        this.configuration = configuration;
    }

    ElasticsearchDiagnostics diagnostics() {
        ConnectionContext connection = connectionContext();
        Optional<ElasticsearchAsyncClient> optionalClient = beanContext.findBean(ElasticsearchAsyncClient.class);
        if (optionalClient.isEmpty()) {
            return ElasticsearchDiagnostics.unavailable(connection, State.NO_CLIENT, NO_CLIENT_MESSAGE);
        }
        try {
            return collect(optionalClient.get(), connection);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            State state = State.DELAYED;
            return ElasticsearchDiagnostics.unavailable(connection, state, messageFor(state));
        } catch (Exception exception) {
            State state = classify(exception);
            return ElasticsearchDiagnostics.unavailable(connection, state, messageFor(state));
        }
    }

    private ElasticsearchDiagnostics collect(ElasticsearchAsyncClient client, ConnectionContext connection) throws Exception {
        long deadline = deadline();
        CompletableFuture<InfoResponse> infoFuture = client.info();
        CompletableFuture<HealthResponse> healthFuture = client.cluster().health(h -> h
            .timeout(t -> t.time(timeoutString()))
            .masterTimeout(t -> t.time(timeoutString())));
        CompletableFuture<List<IndicesRecord>> recordsFuture = client.cat().indices(i -> i
            .h("health", "status", "index", "pri", "rep", "docs.count", "store.size")
            .s("index"))
            .thenApply(response -> response == null || response.indices() == null ? List.of() : response.indices());

        InfoResponse info = await(infoFuture, deadline);
        HealthResponse health = await(healthFuture, deadline);
        List<IndicesRecord> records = await(recordsFuture, deadline);

        ClusterInfo cluster = clusterInfo(info, health);
        HealthSummary healthSummary = healthSummary(health);
        List<IndicesRecord> visibleRecords = records.stream()
            .filter(record -> record.index() != null && !record.index().isBlank())
            .sorted(Comparator.comparing(IndicesRecord::index))
            .toList();
        if (visibleRecords.isEmpty()) {
            return new ElasticsearchDiagnostics(State.NO_VISIBLE_INDICES, NO_VISIBLE_INDICES_MESSAGE, connection, cluster, healthSummary, List.of(), List.of(), 0, 0, false, false);
        }

        int displayLimit = Math.min(configuration.getMaxIndices(), visibleRecords.size());
        List<IndicesRecord> displayedRecords = visibleRecords.subList(0, displayLimit);
        List<String> names = displayedRecords.stream().map(IndicesRecord::index).toList();
        List<String> warnings = new ArrayList<>();
        GetAliasResponse aliasResponse = readAliases(client, names, warnings);
        GetMappingResponse mappingResponse = readMappings(client, names, warnings);

        MappingCollector mappingCollector = new MappingCollector(configuration.getMaxMappingFields());
        List<IndexSummary> indices = displayedRecords.stream()
            .map(record -> indexSummary(record, aliasResponse, mappingResponse, mappingCollector))
            .toList();

        State state = warnings.isEmpty() ? State.AVAILABLE : State.PARTIAL;
        String message = warnings.isEmpty() ? "Elasticsearch cluster metadata is available." : PARTIAL_MESSAGE;
        return new ElasticsearchDiagnostics(state, message, connection, cluster, healthSummary, indices, warnings, visibleRecords.size(), indices.size(), visibleRecords.size() > indices.size(), mappingCollector.truncated());
    }

    private GetAliasResponse readAliases(ElasticsearchAsyncClient client, List<String> names, List<String> warnings) throws Exception {
        try {
            return await(client.indices().getAlias(a -> a.index(names).ignoreUnavailable(true).allowNoIndices(true).masterTimeout(t -> t.time(timeoutString()))));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (Exception exception) {
            warnings.add("Aliases are hidden or unavailable for the current Elasticsearch credentials.");
            return null;
        }
    }

    private GetMappingResponse readMappings(ElasticsearchAsyncClient client, List<String> names, List<String> warnings) throws Exception {
        try {
            return await(client.indices().getMapping(m -> m.index(names).ignoreUnavailable(true).allowNoIndices(true).masterTimeout(t -> t.time(timeoutString()))));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (Exception exception) {
            warnings.add("Mappings are hidden or unavailable for the current Elasticsearch credentials.");
            return null;
        }
    }

    private IndexSummary indexSummary(IndicesRecord record,
                                      GetAliasResponse aliasResponse,
                                      GetMappingResponse mappingResponse,
                                      MappingCollector mappingCollector) {
        String index = record.index();
        List<String> aliases = aliases(index, aliasResponse);
        boolean aliasesTruncated = aliases.size() > configuration.getMaxAliasesPerIndex();
        List<String> displayedAliases = aliases.stream().limit(configuration.getMaxAliasesPerIndex()).toList();
        List<MappingField> fields = mappingCollector.fields(index, mappingResponse);
        int fieldCount = mappingCollector.fieldCount(index, mappingResponse);
        return new IndexSummary(
            index,
            safe(record.health()),
            safe(record.status()),
            safe(record.pri()),
            safe(record.rep()),
            safe(record.docsCount()),
            safe(record.storeSize()),
            displayedAliases,
            aliasesTruncated,
            fieldCount,
            fields);
    }

    private List<String> aliases(String index, GetAliasResponse response) {
        if (response == null) {
            return List.of();
        }
        IndexAliases indexAliases = response.aliases().get(index);
        if (indexAliases == null || indexAliases.aliases().isEmpty()) {
            return List.of();
        }
        return indexAliases.aliases().keySet().stream().sorted().toList();
    }

    private ClusterInfo clusterInfo(InfoResponse info, HealthResponse health) {
        String clusterName = safe(info.clusterName());
        if (clusterName.isEmpty()) {
            clusterName = safe(health.clusterName());
        }
        return new ClusterInfo(
            clusterName,
            safe(info.clusterUuid()),
            safe(info.name()),
            info.version() == null ? "" : safe(info.version().number()),
            safe(info.tagline()));
    }

    private HealthSummary healthSummary(HealthResponse health) {
        String status = health.status() == null ? "unknown" : health.status().jsonValue();
        return new HealthSummary(
            status,
            healthBadgeClass(status),
            health.timedOut(),
            health.numberOfNodes(),
            health.numberOfDataNodes(),
            health.activeShards(),
            health.activePrimaryShards(),
            health.relocatingShards(),
            health.initializingShards(),
            health.unassignedShards(),
            health.delayedUnassignedShards(),
            health.numberOfPendingTasks(),
            safe(health.activeShardsPercent()));
    }

    private ConnectionContext connectionContext() {
        Set<String> hosts = new LinkedHashSet<>();
        environment.getProperty("elasticsearch.http-hosts", String[].class)
            .ifPresent(values -> addHosts(hosts, values));
        environment.getProperty("elasticsearch.httpHosts", String[].class)
            .ifPresent(values -> addHosts(hosts, values));
        beanContext.findBean(DefaultElasticsearchConfiguration.class)
            .map(DefaultElasticsearchConfiguration::getHttpHosts)
            .ifPresent(values -> {
                for (HttpHost host : values) {
                    if (host != null) {
                        hosts.add(sanitizeHost(host.toURI()));
                    }
                }
            });
        beanContext.findBean(RestClient.class)
            .ifPresent(client -> client.getNodes().forEach(node -> hosts.add(sanitizeHost(node.getHost().toURI()))));
        return new ConnectionContext(DEFAULT_CLIENT_BEAN, hosts.stream().filter(host -> !host.isBlank()).toList());
    }

    private static void addHosts(Set<String> hosts, String[] values) {
        for (String value : values) {
            hosts.add(sanitizeHost(value));
        }
    }

    static String sanitizeHost(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SECRET_QUERY_PARAMETER.matcher(value.trim()).replaceAll("$1=***");
        try {
            URI uri = new URI(sanitized);
            if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                String authority = uri.getRawAuthority();
                int at = authority.lastIndexOf('@');
                if (at >= 0) {
                    authority = "***@" + authority.substring(at + 1);
                }
                return new URI(uri.getScheme(), authority, uri.getRawPath(), null, null).toString();
            }
        } catch (URISyntaxException ignored) {
            int scheme = sanitized.indexOf("://");
            int at = sanitized.lastIndexOf('@');
            if (scheme >= 0 && at > scheme) {
                return sanitized.substring(0, scheme + 3) + "***@" + sanitized.substring(at + 1);
            }
        }
        int query = sanitized.indexOf('?');
        return query >= 0 ? sanitized.substring(0, query) : sanitized;
    }

    private <T> T await(CompletableFuture<T> future) throws Exception {
        return await(future, deadline());
    }

    private <T> T await(CompletableFuture<T> future, long deadlineNanos) throws Exception {
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                future.cancel(true);
                throw new TimeoutException();
            }
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private long deadline() {
        return System.nanoTime() + configuration.getProbeTimeout().toNanos();
    }

    private String timeoutString() {
        return configuration.getProbeTimeout().toMillis() + "ms";
    }

    private static State classify(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof TimeoutException) {
            return State.DELAYED;
        }
        if (cause instanceof ElasticsearchException elasticsearchException) {
            int status = elasticsearchException.status();
            if (status == 401) {
                return State.AUTHENTICATION_FAILED;
            }
            if (status == 403) {
                return State.AUTHORIZATION_FAILED;
            }
            if (status == 400 || status == 404) {
                return State.UNSUPPORTED;
            }
            return State.ERROR;
        }
        if (cause instanceof JsonpMappingException) {
            return State.UNSUPPORTED;
        }
        if (cause instanceof IOException) {
            return State.UNAVAILABLE;
        }
        return State.ERROR;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String messageFor(State state) {
        return switch (state) {
            case NO_CLIENT -> NO_CLIENT_MESSAGE;
            case UNAVAILABLE -> "The configured Elasticsearch cluster is unreachable from this application.";
            case AUTHENTICATION_FAILED -> "Elasticsearch rejected the configured credentials.";
            case AUTHORIZATION_FAILED -> "The current Elasticsearch credentials do not have permission to read cluster or index metadata.";
            case UNSUPPORTED -> "Elasticsearch returned metadata in a shape this panel cannot read.";
            case DELAYED -> "Elasticsearch did not respond before the configured diagnostics timeout.";
            default -> "Elasticsearch diagnostics are unavailable.";
        };
    }

    private static String healthBadgeClass(String status) {
        return switch (status.toLowerCase(Locale.ENGLISH)) {
            case "green" -> "badge-primary";
            case "yellow" -> "badge-secondary";
            case "red" -> "badge-destructive";
            default -> "badge-secondary";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class MappingCollector {
        private final int limit;
        private int displayed;
        private boolean truncated;

        private MappingCollector(int limit) {
            this.limit = limit;
        }

        private List<MappingField> fields(String index, GetMappingResponse response) {
            if (response == null) {
                return List.of();
            }
            IndexMappingRecord mappingRecord = response.mappings().get(index);
            if (mappingRecord == null || mappingRecord.mappings() == null) {
                return List.of();
            }
            List<MappingField> fields = new ArrayList<>();
            collectFields("", mappingRecord.mappings().properties(), fields);
            return fields;
        }

        private int fieldCount(String index, GetMappingResponse response) {
            if (response == null) {
                return 0;
            }
            IndexMappingRecord mappingRecord = response.mappings().get(index);
            if (mappingRecord == null || mappingRecord.mappings() == null) {
                return 0;
            }
            return countFields(mappingRecord.mappings());
        }

        private boolean truncated() {
            return truncated;
        }

        private void collectFields(String prefix, Map<String, Property> properties, List<MappingField> fields) {
            if (properties == null || properties.isEmpty()) {
                return;
            }
            if (displayed >= limit) {
                truncated = true;
                return;
            }
            for (Map.Entry<String, Property> entry : properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (displayed >= limit) {
                    truncated = true;
                    return;
                }
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                Property property = entry.getValue();
                fields.add(new MappingField(path, propertyType(property)));
                displayed++;
                collectFields(path, nestedProperties(property), fields);
                collectFields(path, multiFields(property), fields);
            }
        }

        private int countFields(TypeMapping mapping) {
            return countProperties(mapping.properties());
        }

        private int countProperties(Map<String, Property> properties) {
            if (properties == null || properties.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (Property property : properties.values()) {
                count++;
                count += countProperties(nestedProperties(property));
                count += countProperties(multiFields(property));
            }
            return count;
        }

        private Map<String, Property> nestedProperties(Property property) {
            Object value = property._get();
            if (value instanceof PropertyBase propertyBase) {
                return propertyBase.properties();
            }
            return Map.of();
        }

        private Map<String, Property> multiFields(Property property) {
            Object value = property._get();
            if (value instanceof PropertyBase propertyBase) {
                return propertyBase.fields();
            }
            return Map.of();
        }

        private static String propertyType(Property property) {
            if (property._kind() == Property.Kind._Custom) {
                return property._customKind();
            }
            return property._kind().jsonValue();
        }
    }
}
