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
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cat.ElasticsearchCatAsyncClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterAsyncClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesAsyncClient;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.util.DateTime;
import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.elasticsearch.DefaultElasticsearchConfiguration;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.AUTHENTICATION_FAILED;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.AUTHORIZATION_FAILED;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.DELAYED;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.NO_CLIENT;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.NO_VISIBLE_INDICES;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.PARTIAL;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.UNSUPPORTED;
import static io.micronaut.controlpanel.panels.elasticsearch.ElasticsearchDiagnostics.State.UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchDiagnosticsServiceTest {

    @Test
    void sanitizesConfiguredHosts() {
        assertEquals("http://***@localhost:9200", ElasticsearchDiagnosticsService.sanitizeHost("http://elastic:secret@localhost:9200"));
        assertEquals("https://localhost:9200", ElasticsearchDiagnosticsService.sanitizeHost("https://localhost:9200?api_key=abc123"));
        assertEquals("http://***@example.com:9200/path", ElasticsearchDiagnosticsService.sanitizeHost("http://user:pass@example.com:9200/path?token=abc"));
    }

    @Test
    void reportsNoClientWithoutFailing() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        DefaultElasticsearchConfiguration elasticsearchConfiguration = mock(DefaultElasticsearchConfiguration.class);
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.of(elasticsearchConfiguration));
        when(elasticsearchConfiguration.getHttpHosts()).thenReturn(new HttpHost[] { new HttpHost("localhost", 9200, "https") });
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment).diagnostics();

        assertEquals(NO_CLIENT, diagnostics.state());
        assertTrue(diagnostics.connection().hasHosts());
        assertEquals("https://localhost:9200", diagnostics.connection().hosts().get(0));
    }

    @Test
    void readsClusterHealthIndicesAliasesAndMappings() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.of(new String[] { "http://elastic:secret@localhost:9200" }));
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment).diagnostics();

        assertEquals(ElasticsearchDiagnostics.State.AVAILABLE, diagnostics.state());
        assertEquals("orders", diagnostics.cluster().name());
        assertEquals("green", diagnostics.health().status());
        assertEquals(1, diagnostics.indexCount());
        assertEquals("orders", diagnostics.indices().get(0).name());
        assertEquals("orders-read", diagnostics.indices().get(0).aliases().get(0));
        assertEquals(3, diagnostics.indices().get(0).mappingFieldCount());
        assertTrue(diagnostics.indices().get(0).mappingPreview().stream()
            .anyMatch(field -> field.name().equals("id") && field.type().equals("keyword")));
        assertFalse(diagnostics.connection().hosts().get(0).contains("secret"));
    }

    @Test
    void mapsUnavailableClusterToNonSecretState() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        when(client.info()).thenReturn(CompletableFuture.failedFuture(new IOException("Connection refused at http://elastic:secret@localhost:9200")));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment).diagnostics();

        assertEquals(UNAVAILABLE, diagnostics.state());
        assertFalse(diagnostics.message().contains("secret"));
    }

    @Test
    void mapsAuthorizationFailureToNonSecretState() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        when(client.info()).thenReturn(CompletableFuture.failedFuture(elasticsearchException(403)));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment).diagnostics();

        assertEquals(AUTHORIZATION_FAILED, diagnostics.state());
        assertFalse(diagnostics.message().contains("security_exception"));
    }

    @Test
    void mapsAliasOrMappingFailureToPartialData() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        ElasticsearchIndicesAsyncClient indicesClient = client.indices();
        when(indicesClient.getAlias(any(java.util.function.Function.class))).thenReturn(CompletableFuture.failedFuture(elasticsearchException(403)));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment).diagnostics();

        assertEquals(PARTIAL, diagnostics.state());
        assertTrue(diagnostics.hasWarnings());
        assertEquals("orders", diagnostics.indices().get(0).name());
    }

    @Test
    void mapsNullCatIndicesToNoVisibleIndices() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        ElasticsearchCatAsyncClient catClient = client.cat();
        IndicesResponse indicesResponse = mock(IndicesResponse.class);
        when(indicesResponse.indices()).thenReturn(null);
        when(catClient.indices(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(indicesResponse));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment).diagnostics();

        assertEquals(NO_VISIBLE_INDICES, diagnostics.state());
        assertFalse(diagnostics.hasIndices());
    }

    @Test
    void cancelsTimedOutProbe() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mock(ElasticsearchAsyncClient.class);
        ElasticsearchClusterAsyncClient clusterClient = mock(ElasticsearchClusterAsyncClient.class);
        ElasticsearchCatAsyncClient catClient = mock(ElasticsearchCatAsyncClient.class);
        CompletableFuture<InfoResponse> slowInfo = new CompletableFuture<>();
        when(client.info()).thenReturn(slowInfo);
        when(client.cluster()).thenReturn(clusterClient);
        when(client.cat()).thenReturn(catClient);
        when(clusterClient.health(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(healthResponse()));
        when(catClient.indices(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(indicesResponse()));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics diagnostics = service(beanContext, environment, Duration.ofMillis(10)).diagnostics();

        assertEquals(DELAYED, diagnostics.state());
        assertTrue(slowInfo.isCancelled());
    }

    @Test
    void doesNotNormalizeFatalErrors() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        when(client.info()).thenReturn(CompletableFuture.failedFuture(new AssertionError("fatal")));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        assertThrows(AssertionError.class, () -> service(beanContext, environment).diagnostics());
    }

    @Test
    void mapsAuthenticationAndUnsupportedFailuresToSafeStates() {
        BeanContext authenticationBeanContext = mock(BeanContext.class);
        Environment authenticationEnvironment = mock(Environment.class);
        ElasticsearchAsyncClient authenticationClient = mockClient();
        when(authenticationClient.info()).thenReturn(CompletableFuture.failedFuture(elasticsearchException(401)));
        when(authenticationBeanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(authenticationClient));
        when(authenticationBeanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(authenticationBeanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(authenticationEnvironment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(authenticationEnvironment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics authentication = service(authenticationBeanContext, authenticationEnvironment).diagnostics();

        assertEquals(AUTHENTICATION_FAILED, authentication.state());
        assertFalse(authentication.message().contains("security_exception"));

        BeanContext unsupportedBeanContext = mock(BeanContext.class);
        Environment unsupportedEnvironment = mock(Environment.class);
        ElasticsearchAsyncClient unsupportedClient = mockClient();
        when(unsupportedClient.info()).thenReturn(CompletableFuture.failedFuture(elasticsearchException(400)));
        when(unsupportedBeanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(unsupportedClient));
        when(unsupportedBeanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(unsupportedBeanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(unsupportedEnvironment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(unsupportedEnvironment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchDiagnostics unsupported = service(unsupportedBeanContext, unsupportedEnvironment).diagnostics();

        assertEquals(UNSUPPORTED, unsupported.state());
        assertFalse(unsupported.message().contains("security_exception"));
    }

    @Test
    void appliesConfiguredDisplayLimits() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        ElasticsearchAsyncClient client = mockClient();
        ElasticsearchClusterAsyncClient clusterClient = client.cluster();
        ElasticsearchCatAsyncClient catClient = client.cat();
        ElasticsearchIndicesAsyncClient indicesClient = client.indices();
        when(clusterClient.health(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(healthResponse(HealthStatus.Yellow)));
        when(catClient.indices(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(IndicesResponse.of(response -> response
            .indices(index -> index.index("orders-2").health("green").status("open").pri("1").rep("1").docsCount("20").storeSize("16kb"))
            .indices(index -> index.index("orders-1").health("yellow").status("open").pri("1").rep("1").docsCount("10").storeSize("8kb")))));
        when(indicesClient.getAlias(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(GetAliasResponse.of(response -> response
            .aliases("orders-1", aliases -> aliases
                .aliases("orders-read", alias -> alias.isWriteIndex(false))
                .aliases("orders-search", alias -> alias.isWriteIndex(false))))));
        when(indicesClient.getMapping(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(mappingResponse("orders-1")));
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.of(client));
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ElasticsearchControlPanelConfiguration configuration = new ElasticsearchControlPanelConfiguration();
        configuration.setMaxIndices(1);
        configuration.setMaxAliasesPerIndex(1);
        configuration.setMaxMappingFields(1);
        ElasticsearchDiagnostics diagnostics = new ElasticsearchDiagnosticsService(beanContext, environment, configuration).diagnostics();

        assertEquals(2, diagnostics.indexCount());
        assertEquals(1, diagnostics.displayedIndexCount());
        assertTrue(diagnostics.indicesTruncated());
        assertTrue(diagnostics.mappingFieldsTruncated());
        assertEquals("yellow", diagnostics.health().status());
        assertEquals(ElasticsearchDiagnostics.BADGE_SECONDARY, diagnostics.health().statusBadgeClass());
        ElasticsearchDiagnostics.IndexSummary index = diagnostics.indices().get(0);
        assertEquals("orders-1", index.name());
        assertEquals(List.of("orders-read"), index.aliases());
        assertTrue(index.aliasesTruncated());
        assertEquals(3, index.mappingFieldCount());
        assertEquals(1, index.mappingPreview().size());
    }

    @Test
    void normalizesConfigurationAndViewModelDefaults() {
        ElasticsearchControlPanelConfiguration configuration = new ElasticsearchControlPanelConfiguration();
        assertEquals(50, configuration.getMaxIndices());
        assertEquals(10, configuration.getMaxAliasesPerIndex());
        assertEquals(50, configuration.getMaxMappingFields());
        assertEquals(Duration.ofSeconds(5), configuration.getProbeTimeout());

        configuration.setMaxIndices(2);
        configuration.setMaxAliasesPerIndex(3);
        configuration.setMaxMappingFields(4);
        configuration.setProbeTimeout(Duration.ofMillis(250));
        assertEquals(2, configuration.getMaxIndices());
        assertEquals(3, configuration.getMaxAliasesPerIndex());
        assertEquals(4, configuration.getMaxMappingFields());
        assertEquals(Duration.ofMillis(250), configuration.getProbeTimeout());

        configuration.setMaxIndices(0);
        configuration.setMaxAliasesPerIndex(-1);
        configuration.setMaxMappingFields(0);
        configuration.setProbeTimeout(Duration.ZERO);
        assertEquals(50, configuration.getMaxIndices());
        assertEquals(10, configuration.getMaxAliasesPerIndex());
        assertEquals(50, configuration.getMaxMappingFields());
        assertEquals(Duration.ofSeconds(5), configuration.getProbeTimeout());

        ElasticsearchDiagnostics diagnostics = new ElasticsearchDiagnostics(
            PARTIAL,
            "partial",
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            false,
            false);
        assertTrue(diagnostics.available());
        assertFalse(diagnostics.hasIndices());
        assertFalse(diagnostics.hasWarnings());
        assertEquals("Partial", diagnostics.stateLabel());
        assertEquals(ElasticsearchDiagnostics.BADGE_SECONDARY, diagnostics.badgeClass());

        ElasticsearchDiagnostics.ConnectionContext defaultConnection = new ElasticsearchDiagnostics.ConnectionContext("", null);
        assertEquals("default", defaultConnection.clientBean());
        assertFalse(defaultConnection.hasHosts());

        ElasticsearchDiagnostics.ConnectionContext configuredConnection = new ElasticsearchDiagnostics.ConnectionContext("search", List.of("https://localhost:9200"));
        assertTrue(configuredConnection.hasHosts());

        ElasticsearchDiagnostics.IndexSummary indexSummary = new ElasticsearchDiagnostics.IndexSummary("orders", "", "", "", "", "", "", null, false, 0, null);
        assertFalse(indexSummary.hasAliases());
        assertFalse(indexSummary.hasMappingPreview());
    }

    @Test
    void controlPanelDelegatesConfigurationAndBody() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        when(beanContext.findBean(ElasticsearchAsyncClient.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(DefaultElasticsearchConfiguration.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(org.elasticsearch.client.RestClient.class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.http-hosts", String[].class)).thenReturn(Optional.empty());
        when(environment.getProperty("elasticsearch.httpHosts", String[].class)).thenReturn(Optional.empty());

        ControlPanelConfiguration controlPanelConfiguration = new ControlPanelConfiguration(ElasticsearchControlPanel.NAME);
        controlPanelConfiguration.setTitle("Elasticsearch");
        controlPanelConfiguration.setIcon("si si-elasticsearch");
        controlPanelConfiguration.setOrder(25);
        ElasticsearchControlPanel panel = new ElasticsearchControlPanel(service(beanContext, environment), controlPanelConfiguration);

        assertEquals(ElasticsearchControlPanel.NAME, panel.getName());
        assertEquals("Elasticsearch", panel.getTitle());
        assertEquals("si si-elasticsearch", panel.getIcon());
        assertEquals(25, panel.getOrder());
        assertTrue(panel.isEnabled());
        assertEquals(ElasticsearchControlPanel.CATEGORY, panel.getCategory());
        assertEquals("Inspect", panel.getDetailLinkName());
        assertEquals(NO_CLIENT, panel.getBody().state());
    }

    private static ElasticsearchDiagnosticsService service(BeanContext beanContext, Environment environment) {
        return service(beanContext, environment, Duration.ofSeconds(1));
    }

    private static ElasticsearchDiagnosticsService service(BeanContext beanContext, Environment environment, Duration timeout) {
        ElasticsearchControlPanelConfiguration configuration = new ElasticsearchControlPanelConfiguration();
        configuration.setProbeTimeout(timeout);
        return new ElasticsearchDiagnosticsService(beanContext, environment, configuration);
    }

    private static ElasticsearchAsyncClient mockClient() {
        ElasticsearchAsyncClient client = mock(ElasticsearchAsyncClient.class);
        ElasticsearchClusterAsyncClient clusterClient = mock(ElasticsearchClusterAsyncClient.class);
        ElasticsearchCatAsyncClient catClient = mock(ElasticsearchCatAsyncClient.class);
        ElasticsearchIndicesAsyncClient indicesClient = mock(ElasticsearchIndicesAsyncClient.class);
        when(client.info()).thenReturn(CompletableFuture.completedFuture(infoResponse()));
        when(client.cluster()).thenReturn(clusterClient);
        when(client.cat()).thenReturn(catClient);
        when(client.indices()).thenReturn(indicesClient);
        when(clusterClient.health(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(healthResponse()));
        when(catClient.indices(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(indicesResponse()));
        when(indicesClient.getAlias(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(aliasResponse()));
        when(indicesClient.getMapping(any(java.util.function.Function.class))).thenReturn(CompletableFuture.completedFuture(mappingResponse()));
        return client;
    }

    private static InfoResponse infoResponse() {
        return InfoResponse.of(info -> info
            .clusterName("orders")
            .clusterUuid("uuid-1")
            .name("node-1")
            .version(version -> version
                .number("9.4.0")
                .buildDate(DateTime.of("2026-05-01T00:00:00Z"))
                .buildFlavor("default")
                .buildHash("abc123")
                .buildSnapshot(false)
                .buildType("tar")
                .luceneVersion("10.0.0")
                .minimumIndexCompatibilityVersion("8.0.0")
                .minimumWireCompatibilityVersion("8.0.0"))
            .tagline("You Know, for Search"));
    }

    private static HealthResponse healthResponse() {
        return healthResponse(HealthStatus.Green);
    }

    private static HealthResponse healthResponse(HealthStatus status) {
        return HealthResponse.of(health -> health
            .clusterName("orders")
            .status(status)
            .numberOfNodes(1)
            .numberOfDataNodes(1)
            .numberOfInFlightFetch(0)
            .activeShards(2)
            .activePrimaryShards(1)
            .relocatingShards(0)
            .initializingShards(0)
            .unassignedShards(0)
            .unassignedPrimaryShards(0)
            .delayedUnassignedShards(0)
            .numberOfPendingTasks(0)
            .taskMaxWaitingInQueueMillis(0)
            .activeShardsPercent("100.0%")
            .activeShardsPercentAsNumber(100.0)
            .timedOut(false));
    }

    private static IndicesResponse indicesResponse() {
        return IndicesResponse.of(response -> response.indices(index -> index
            .index("orders")
            .health("green")
            .status("open")
            .pri("1")
            .rep("1")
            .docsCount("10")
            .storeSize("8kb")));
    }

    private static GetAliasResponse aliasResponse() {
        return GetAliasResponse.of(response -> response.aliases("orders", aliases -> aliases.aliases("orders-read", alias -> alias.isWriteIndex(false))));
    }

    private static GetMappingResponse mappingResponse() {
        return mappingResponse("orders");
    }

    private static GetMappingResponse mappingResponse(String index) {
        return GetMappingResponse.of(response -> response.mappings(index, mapping -> mapping.mappings(type -> type
            .properties("id", property -> property.keyword(keyword -> keyword))
            .properties("customer", property -> property.object(object -> object.properties("name", nested -> nested.text(text -> text)))))));
    }

    private static ElasticsearchException elasticsearchException(int status) {
        ErrorResponse response = ErrorResponse.of(error -> error
            .status(status)
            .error(cause -> cause.type("security_exception").reason("secret raw response")));
        return new ElasticsearchException("elasticsearch", response);
    }
}
