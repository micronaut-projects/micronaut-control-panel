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
package example;

import jakarta.inject.Singleton;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cat.IndicesResponse;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.TransportOptions;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Demo transport that lets the example app render the OpenSearch panel without a running cluster.
 */
@Singleton
final class DemoOpenSearchTransport implements OpenSearchTransport {

    private static final JsonpMapper JSONP_MAPPER = new JacksonJsonpMapper();
    private static final TransportOptions OPTIONS = TransportOptions.builder().build();

    @Override
    @SuppressWarnings("unchecked")
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request,
                                                                  Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                                                                  TransportOptions options) throws IOException {
        String requestUrl = endpoint.requestUrl(request);
        if (requestUrl.contains("_cluster/health")) {
            return (ResponseT) health();
        }
        if (requestUrl.contains("_cat/indices")) {
            return (ResponseT) indices();
        }
        if (requestUrl.contains("_alias")) {
            return (ResponseT) aliases();
        }
        if (requestUrl.contains("_mapping")) {
            return (ResponseT) mappings();
        }
        throw new IOException("Unsupported demo OpenSearch request: " + requestUrl);
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request,
                                                                                         Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                                                                                         TransportOptions options) {
        try {
            return CompletableFuture.completedFuture(performRequest(request, endpoint, options));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public JsonpMapper jsonpMapper() {
        return JSONP_MAPPER;
    }

    @Override
    public TransportOptions options() {
        return OPTIONS;
    }

    @Override
    public void close() {
    }

    private static HealthResponse health() {
        return HealthResponse.of(b -> b
            .clusterName("local-dev")
            .status(HealthStatus.Yellow)
            .timedOut(false)
            .numberOfNodes(1)
            .numberOfDataNodes(1)
            .numberOfPendingTasks(0)
            .numberOfInFlightFetch(0)
            .activeShardsPercentAsNumber(75.0)
            .activePrimaryShards(6)
            .activeShards(9)
            .taskMaxWaitingInQueueMillis(0)
            .relocatingShards(0)
            .initializingShards(0)
            .unassignedShards(3)
            .delayedUnassignedShards(0)
        );
    }

    private static IndicesResponse indices() {
        return IndicesResponse.of(b -> b.valueBody(
            index("movies", "yellow", "42", "128kb"),
            index("orders-2026-05", "green", "1280", "2.4mb")
        ));
    }

    private static GetAliasResponse aliases() {
        return GetAliasResponse.of(b -> b.result(Map.of(
            "movies", IndexAliases.of(a -> a.aliases("current-movies", alias -> alias)),
            "orders-2026-05", IndexAliases.of(a -> a.aliases("orders-write", alias -> alias))
        )));
    }

    private static GetMappingResponse mappings() {
        return GetMappingResponse.of(b -> b.result(Map.of(
            "movies", IndexMappingRecord.of(m -> m.mappings(TypeMapping.of(tm -> tm
                .properties("title", Property.of(p -> p.text(t -> t)))
                .properties("releaseYear", Property.of(p -> p.integer(i -> i)))
                .properties("genre", Property.of(p -> p.keyword(k -> k)))
            ))),
            "orders-2026-05", IndexMappingRecord.of(m -> m.mappings(TypeMapping.of(tm -> tm
                .properties("id", Property.of(p -> p.keyword(k -> k)))
                .properties("customerId", Property.of(p -> p.keyword(k -> k)))
                .properties("total", Property.of(p -> p.double_(d -> d)))
            )))
        )));
    }

    private static IndicesRecord index(String name, String health, String docs, String storeSize) {
        return IndicesRecord.of(b -> b
            .index(name)
            .health(health)
            .status("open")
            .pri("1")
            .rep("1")
            .docsCount(docs)
            .storeSize(storeSize)
        );
    }
}
