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

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.panels.opensearch.model.DiagnosticState;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cat.IndicesResponse;
import org.opensearch.client.opensearch.cat.IndicesRequest;
import org.opensearch.client.opensearch.cat.OpenSearchCatClient;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.cluster.OpenSearchClusterClient;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.GetMappingRequest;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.opensearch.client.util.ObjectBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenSearchDiagnosticsServiceTest {

    @Test
    void sanitizesConfiguredConnectionMetadata() throws Exception {
        OpenSearchDiagnosticsService service = service(Map.of(
            "micronaut.opensearch.rest-client.http-hosts[0]", "http://admin:secret@localhost:9200?token=secret",
            "micronaut.opensearch.aws.endpoint", "search-example.us-east-1.es.amazonaws.com",
            "micronaut.opensearch.aws.signing-region", "us-east-1"
        ), client(
            health(HealthStatus.Green),
            IndicesResponse.of(b -> b.valueBody(List.of())),
            GetAliasResponse.of(b -> b.result(Map.of())),
            GetMappingResponse.of(b -> b.result(Map.of()))
        ), 25, 20);

        var diagnostics = service.diagnostics();

        assertEquals(DiagnosticState.AVAILABLE, diagnostics.state());
        assertEquals("GREEN", diagnostics.statusLabel());
        assertEquals("http://***@localhost:9200", diagnostics.connection().hosts().get(0));
        assertEquals("search-example.us-east-1.es.amazonaws.com", diagnostics.connection().amazonEndpoint());
        assertEquals("us-east-1", diagnostics.connection().signingRegion());
        assertFalse(diagnostics.hasIndices());
    }

    @Test
    void readsClusterIndexAliasAndMappingSummaries() throws Exception {
        OpenSearchDiagnosticsService service = service(Map.of(
            "micronaut.opensearch.httpclient5.http-hosts[0]", "http://localhost:9200"
        ), client(
            health(HealthStatus.Yellow),
            IndicesResponse.of(b -> b.valueBody(index("movies"), index("orders"))),
            GetAliasResponse.of(b -> b.result(Map.of(
                "movies", IndexAliases.of(a -> a.aliases("current-movies", alias -> alias)),
                "orders", IndexAliases.of(a -> a.aliases(Map.of()))
            ))),
            GetMappingResponse.of(b -> b.result(Map.of(
                "movies", IndexMappingRecord.of(m -> m.mappings(TypeMapping.of(tm -> tm
                    .properties("title", Property.of(p -> p.text(t -> t)))
                    .properties("releaseYear", Property.of(p -> p.integer(i -> i)))
                ))),
                "orders", IndexMappingRecord.of(m -> m.mappings(TypeMapping.of(tm -> tm
                    .properties("id", Property.of(p -> p.keyword(k -> k)))
                )))
            )))
        ), 1, 1);

        var diagnostics = service.diagnostics();

        assertEquals(DiagnosticState.AVAILABLE, diagnostics.state());
        assertEquals("YELLOW", diagnostics.statusLabel());
        assertEquals(1, diagnostics.indices().size());
        assertTrue(diagnostics.indicesTruncated());
        var movies = diagnostics.indices().get(0);
        assertEquals("movies", movies.name());
        assertEquals(List.of("current-movies"), movies.aliases());
        assertEquals(1, movies.mappingFields().size());
        assertTrue(movies.mappingTruncated());
    }

    @Test
    void classifiesAuthorizationFailureWithoutRawResponseBody() throws Exception {
        OpenSearchClient client = mock(OpenSearchClient.class);
        OpenSearchClusterClient cluster = mock(OpenSearchClusterClient.class);
        when(client.cluster()).thenReturn(cluster);
        when(cluster.health()).thenThrow(new OpenSearchException(ErrorResponse.of(e -> e
            .status(403)
            .error(error -> error.type("security_exception").reason("raw secret details"))
        )));

        OpenSearchDiagnosticsService service = service(Map.of(), client, 25, 20);

        var diagnostics = service.diagnostics();

        assertEquals(DiagnosticState.AUTHORIZATION_FAILED, diagnostics.state());
        assertEquals("Authorization", diagnostics.statusLabel());
        assertTrue(diagnostics.message().contains("privileges"));
        assertFalse(diagnostics.message().contains("raw secret details"));
    }

    @Test
    void redactsSecretLikeEndpointSegments() {
        assertEquals("https://***@localhost:9200/path", OpenSearchDiagnosticsService.sanitizedEndpoint("https://user:pass@localhost:9200/path?access_key=secret"));
        assertEquals("https://example.com/path/***", OpenSearchDiagnosticsService.sanitizedEndpoint("https://example.com/path/secret_key=value"));
    }

    private static OpenSearchDiagnosticsService service(Map<String, Object> properties,
                                                        OpenSearchClient client,
                                                        int maxIndices,
                                                        int maxMappingFields) {
        ApplicationContext context = ApplicationContext.run(properties);
        OpenSearchDiagnosticsConfiguration configuration = mock(OpenSearchDiagnosticsConfiguration.class);
        when(configuration.getMaxIndices()).thenReturn(maxIndices);
        when(configuration.getMaxMappingFields()).thenReturn(maxMappingFields);
        return new OpenSearchDiagnosticsService(client, context.getEnvironment(), configuration);
    }

    private static OpenSearchClient client(HealthResponse health,
                                           IndicesResponse indicesResponse,
                                           GetAliasResponse aliasResponse,
                                           GetMappingResponse mappingResponse) throws Exception {
        OpenSearchClient client = mock(OpenSearchClient.class);
        OpenSearchClusterClient cluster = mock(OpenSearchClusterClient.class);
        OpenSearchCatClient cat = mock(OpenSearchCatClient.class);
        OpenSearchIndicesClient indices = mock(OpenSearchIndicesClient.class);
        when(client.cluster()).thenReturn(cluster);
        when(client.cat()).thenReturn(cat);
        when(client.indices()).thenReturn(indices);
        when(cluster.health()).thenReturn(health);
        when(cat.indices(anyCatIndicesRequest())).thenReturn(indicesResponse);
        when(indices.getAlias(anyGetAliasRequest())).thenReturn(aliasResponse);
        when(indices.getMapping(anyGetMappingRequest())).thenReturn(mappingResponse);
        return client;
    }

    private static Function<IndicesRequest.Builder, ObjectBuilder<IndicesRequest>> anyCatIndicesRequest() {
        return any();
    }

    private static Function<GetAliasRequest.Builder, ObjectBuilder<GetAliasRequest>> anyGetAliasRequest() {
        return any();
    }

    private static Function<GetMappingRequest.Builder, ObjectBuilder<GetMappingRequest>> anyGetMappingRequest() {
        return any();
    }

    private static HealthResponse health(HealthStatus status) {
        return HealthResponse.of(b -> b
            .clusterName("local-dev")
            .status(status)
            .timedOut(false)
            .numberOfNodes(1)
            .numberOfDataNodes(1)
            .numberOfPendingTasks(0)
            .numberOfInFlightFetch(0)
            .activeShardsPercentAsNumber(100.0)
            .activePrimaryShards(1)
            .activeShards(1)
            .taskMaxWaitingInQueueMillis(0)
            .relocatingShards(0)
            .initializingShards(0)
            .unassignedShards(0)
            .delayedUnassignedShards(0)
        );
    }

    private static IndicesRecord index(String name) {
        return IndicesRecord.of(b -> b
            .index(name)
            .health("yellow")
            .status("open")
            .pri("1")
            .rep("1")
            .docsCount("42")
            .storeSize("128kb")
        );
    }
}
