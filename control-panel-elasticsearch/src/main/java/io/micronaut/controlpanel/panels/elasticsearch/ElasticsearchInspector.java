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
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.elasticsearch.DefaultElasticsearchConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.apache.http.HttpHost;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Collects the hosts, health and indices of the Elasticsearch cluster of the application.
 *
 * <p>The dashboard card only reads the configuration. The detail view sends three read-only requests (cluster info,
 * cluster health and the index list) in parallel, and gives up after {@link #TIMEOUT}. Errors are shown by type,
 * never with the server's message, and hosts are shown without user info.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = DefaultElasticsearchConfiguration.class)
public class ElasticsearchInspector {

    static final Duration TIMEOUT = Duration.ofSeconds(3);
    static final int MAX_INDICES = 100;

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchInspector.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public ElasticsearchInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @param detailed whether to request the cluster health and indices
     * @return the cluster
     */
    public ElasticsearchInfo inspect(boolean detailed) {
        List<String> hosts = hosts();
        boolean clientOpen = clientOpen();
        if (!detailed) {
            return new ElasticsearchInfo(hosts, clientOpen, null, List.of(), 0, null);
        }
        Optional<ElasticsearchAsyncClient> client = asyncClient();
        if (client.isEmpty()) {
            return new ElasticsearchInfo(hosts, clientOpen, null, List.of(), 0, "no Elasticsearch client");
        }
        CompletableFuture<InfoResponse> info = client.get().info();
        CompletableFuture<HealthResponse> health = client.get().cluster().health();
        CompletableFuture<IndicesResponse> indices = client.get().cat().indices(request -> request.expandWildcards(ExpandWildcard.Open));
        try {
            CompletableFuture.allOf(info, health, indices).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            List<ElasticsearchInfo.Index> visible = indices.join().indices().stream()
                .filter(index -> index.index() != null && !index.index().startsWith("."))
                .map(ElasticsearchInspector::index)
                .sorted(Comparator.comparing(ElasticsearchInfo.Index::name))
                .toList();
            return new ElasticsearchInfo(
                hosts,
                clientOpen,
                cluster(info.join(), health.join()),
                visible.stream().limit(MAX_INDICES).toList(),
                visible.size(),
                null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ElasticsearchInfo(hosts, clientOpen, null, List.of(), 0, "interrupted");
        } catch (TimeoutException e) {
            LOG.debug("Elasticsearch did not answer within {}", TIMEOUT);
            return new ElasticsearchInfo(hosts, clientOpen, null, List.of(), 0, "no answer within " + TIMEOUT.toSeconds() + "s");
        } catch (ExecutionException | RuntimeException e) {
            LOG.debug("Unable to read the Elasticsearch cluster", e);
            return new ElasticsearchInfo(hosts, clientOpen, null, List.of(), 0, describe(e));
        } finally {
            info.cancel(true);
            health.cancel(true);
            indices.cancel(true);
        }
    }

    private List<String> hosts() {
        try {
            return beanContext.findBean(DefaultElasticsearchConfiguration.class)
                .map(DefaultElasticsearchConfiguration::getHttpHosts)
                .map(hosts -> Arrays.stream(hosts).map(HttpHost::toURI).toList())
                .orElse(List.of());
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the Elasticsearch hosts", e);
            return List.of();
        }
    }

    private boolean clientOpen() {
        return !beanContext.getActiveBeanRegistrations(Qualifiers.byType(ElasticsearchAsyncClient.class)).isEmpty()
            || !beanContext.getActiveBeanRegistrations(Qualifiers.byType(ElasticsearchClient.class)).isEmpty()
            || !beanContext.getActiveBeanRegistrations(Qualifiers.byType(RestClient.class)).isEmpty();
    }

    private Optional<ElasticsearchAsyncClient> asyncClient() {
        try {
            return beanContext.findBean(ElasticsearchAsyncClient.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to create the Elasticsearch client", e);
            return Optional.empty();
        }
    }

    private static ElasticsearchInfo.Cluster cluster(InfoResponse info, HealthResponse health) {
        return new ElasticsearchInfo.Cluster(
            info.clusterName(),
            info.version() == null ? "unknown" : info.version().number(),
            health.status() == null ? "unknown" : health.status().jsonValue(),
            health.numberOfNodes(),
            health.activeShards(),
            health.unassignedShards()
        );
    }

    private static ElasticsearchInfo.Index index(IndicesRecord index) {
        return new ElasticsearchInfo.Index(
            index.index(),
            orEmpty(index.health()),
            orEmpty(index.status()),
            orEmpty(index.docsCount()),
            orEmpty(index.storeSize()),
            orEmpty(index.pri()),
            orEmpty(index.rep())
        );
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /**
     * Describes an error without its message, which can contain the server's response.
     *
     * @param error the error
     * @return the error type, and the HTTP status if Elasticsearch answered with an error
     */
    static String describe(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof java.util.concurrent.CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ElasticsearchException elasticsearch) {
            return "request refused (HTTP " + elasticsearch.status() + ")";
        }
        if (cause instanceof ResponseException response) {
            return "request refused (HTTP " + response.getResponse().getStatusLine().getStatusCode() + ")";
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
