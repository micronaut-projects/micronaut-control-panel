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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.opensearch.conf.OpenSearchConfiguration;
import io.micronaut.opensearch.httpclient5.conf.HttpClient5OpenSearchConfiguration;
import io.micronaut.opensearch.restclient.conf.RestClientOpenSearchConfiguration;
import jakarta.inject.Singleton;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cat.IndicesResponse;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.core.InfoResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Collects the hosts, health and indices of the OpenSearch cluster of the application.
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
@Requires(classes = OpenSearchConfiguration.class)
public class OpenSearchInspector {

    static final Duration TIMEOUT = Duration.ofSeconds(3);
    static final int MAX_INDICES = 100;

    private static final String HTTP_CLIENT5_CONFIGURATION = "io.micronaut.opensearch.httpclient5.conf.HttpClient5OpenSearchConfiguration";
    private static final String REST_CLIENT_CONFIGURATION = "io.micronaut.opensearch.restclient.conf.RestClientOpenSearchConfiguration";

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchInspector.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public OpenSearchInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @param detailed whether to request the cluster health and indices
     * @return the cluster
     */
    public OpenSearchInfo inspect(boolean detailed) {
        List<String> hosts = hosts();
        boolean clientOpen = clientOpen();
        if (!detailed) {
            return new OpenSearchInfo(hosts, clientOpen, null, List.of(), 0, null);
        }
        Optional<OpenSearchAsyncClient> client = asyncClient();
        if (client.isEmpty()) {
            return new OpenSearchInfo(hosts, clientOpen, null, List.of(), 0, "no OpenSearch client");
        }
        CompletableFuture<InfoResponse> info;
        CompletableFuture<HealthResponse> health;
        CompletableFuture<IndicesResponse> indices;
        try {
            info = client.get().info();
            health = client.get().cluster().health();
            indices = client.get().cat().indices(request -> request.expandWildcards(ExpandWildcard.Open));
        } catch (IOException | RuntimeException e) {
            LOG.debug("Unable to send the OpenSearch requests", e);
            return new OpenSearchInfo(hosts, clientOpen, null, List.of(), 0, describe(e));
        }
        try {
            CompletableFuture.allOf(info, health, indices).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            List<OpenSearchInfo.Index> visible = indices.join().valueBody().stream()
                .filter(index -> index.index() != null && !index.index().startsWith("."))
                .map(OpenSearchInspector::index)
                .sorted(Comparator.comparing(OpenSearchInfo.Index::name))
                .toList();
            return new OpenSearchInfo(
                hosts,
                clientOpen,
                cluster(info.join(), health.join()),
                visible.stream().limit(MAX_INDICES).toList(),
                visible.size(),
                null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OpenSearchInfo(hosts, clientOpen, null, List.of(), 0, "interrupted");
        } catch (TimeoutException e) {
            LOG.debug("OpenSearch did not answer within {}", TIMEOUT);
            return new OpenSearchInfo(hosts, clientOpen, null, List.of(), 0, "no answer within " + TIMEOUT.toSeconds() + "s");
        } catch (ExecutionException | RuntimeException e) {
            LOG.debug("Unable to read the OpenSearch cluster", e);
            return new OpenSearchInfo(hosts, clientOpen, null, List.of(), 0, describe(e));
        } finally {
            info.cancel(true);
            health.cancel(true);
            indices.cancel(true);
        }
    }

    private List<String> hosts() {
        List<String> hosts = new ArrayList<>();
        try {
            if (ClassUtils.isPresent(HTTP_CLIENT5_CONFIGURATION, getClass().getClassLoader())) {
                HttpClient5Hosts.addHosts(beanContext, hosts);
            }
            if (ClassUtils.isPresent(REST_CLIENT_CONFIGURATION, getClass().getClassLoader())) {
                RestClientHosts.addHosts(beanContext, hosts);
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("Unable to read the OpenSearch hosts", e);
        }
        return hosts.stream().distinct().toList();
    }

    private static List<String> toUris(HttpHost[] hosts) {
        return Arrays.stream(hosts).map(HttpHost::toURI).toList();
    }

    private boolean clientOpen() {
        return !beanContext.getActiveBeanRegistrations(Qualifiers.byType(OpenSearchAsyncClient.class)).isEmpty()
            || !beanContext.getActiveBeanRegistrations(Qualifiers.byType(OpenSearchClient.class)).isEmpty()
            || !beanContext.getActiveBeanRegistrations(Qualifiers.byType(OpenSearchTransport.class)).isEmpty();
    }

    private Optional<OpenSearchAsyncClient> asyncClient() {
        try {
            return beanContext.findBean(OpenSearchAsyncClient.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to create the OpenSearch client", e);
            return Optional.empty();
        }
    }

    private static OpenSearchInfo.Cluster cluster(InfoResponse info, HealthResponse health) {
        return new OpenSearchInfo.Cluster(
            info.clusterName(),
            info.version().number(),
            health.status().jsonValue(),
            health.numberOfNodes(),
            health.activeShards(),
            health.unassignedShards()
        );
    }

    private static OpenSearchInfo.Index index(IndicesRecord index) {
        return new OpenSearchInfo.Index(
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
     * @return the error type, and the HTTP status if OpenSearch answered with an error
     */
    static String describe(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof java.util.concurrent.CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof OpenSearchException opensearch) {
            return "request refused (HTTP " + opensearch.status() + ")";
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    /**
     * Reads the hosts of the Apache HttpClient 5 transport, in its own class so that it is only loaded when that
     * transport is on the classpath.
     */
    private static final class HttpClient5Hosts {
        static void addHosts(BeanContext beanContext, List<String> hosts) {
            beanContext.getBeansOfType(HttpClient5OpenSearchConfiguration.class)
                .forEach(configuration -> hosts.addAll(toUris(configuration.getHttpHosts())));
        }
    }

    /**
     * Reads the hosts of the REST client transport, in its own class so that it is only loaded when that transport is
     * on the classpath.
     */
    private static final class RestClientHosts {
        static void addHosts(BeanContext beanContext, List<String> hosts) {
            beanContext.getBeansOfType(RestClientOpenSearchConfiguration.class)
                .forEach(configuration -> hosts.addAll(toUris(configuration.getHttpHosts())));
        }
    }
}
