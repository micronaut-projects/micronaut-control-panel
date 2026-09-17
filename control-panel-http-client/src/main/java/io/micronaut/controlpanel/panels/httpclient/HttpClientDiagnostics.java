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
package io.micronaut.controlpanel.panels.httpclient;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Read-only HTTP client diagnostics view model.
 *
 * @param clients clients visible to the running application
 * @param clientCount number of clients
 * @param discoveryClientAvailable whether Micronaut discovery is available
 */
@ReflectiveAccess
public record HttpClientDiagnostics(
    List<HttpClientInfo> clients,
    int clientCount,
    boolean discoveryClientAvailable) {

    public boolean hasClients() {
        return !clients.isEmpty();
    }

    public static HttpClientDiagnostics of(List<HttpClientInfo> clients, boolean discoveryClientAvailable) {
        return new HttpClientDiagnostics(List.copyOf(clients), clients.size(), discoveryClientAvailable);
    }

    /**
     * A single declarative or named Micronaut HTTP client.
     *
     * @param id client id or bean name
     * @param beanType Java type when available
     * @param source source classification
     * @param target target summary
     * @param configuration safe configuration summary
     * @param discovery discovery or fixed-target state
     * @param filters matching filter metadata
     */
    @ReflectiveAccess
    public record HttpClientInfo(
        String id,
        String beanType,
        String source,
        TargetInfo target,
        ConfigurationInfo configuration,
        DiscoveryInfo discovery,
        List<FilterInfo> filters) {

        public boolean hasFilters() {
            return !filters.isEmpty();
        }
    }

    /**
     * Target information.
     *
     * @param kind fixed URL, service id, discovery, or unknown
     * @param value masked target value
     * @param badgeClass badge CSS class
     */
    @ReflectiveAccess
    public record TargetInfo(String kind, String value, String badgeClass) {
    }

    /**
     * Safe HTTP client configuration subset.
     *
     * @param path configured context path
     * @param connectTimeout connect timeout summary
     * @param readTimeout read timeout summary
     * @param requestTimeout request timeout summary
     * @param followRedirects redirect policy summary
     * @param maxRedirects redirect limit summary
     * @param httpVersion HTTP version summary
     * @param plaintextMode plaintext mode summary
     * @param alpnModes ALPN mode summary
     * @param proxy masked proxy summary
     * @param proxyCredentials proxy credential visibility summary
     * @param tls TLS configuration summary
     * @param connectionPool connection pool summary
     */
    @ReflectiveAccess
    public record ConfigurationInfo(
        String path,
        String connectTimeout,
        String readTimeout,
        String requestTimeout,
        String followRedirects,
        String maxRedirects,
        String httpVersion,
        String plaintextMode,
        String alpnModes,
        String proxy,
        String proxyCredentials,
        String tls,
        String connectionPool) {
    }

    /**
     * Discovery information visible through Micronaut service-instance lists.
     *
     * @param available whether instance details are available
     * @param message explanation for the state
     * @param instances safe instance list
     */
    @ReflectiveAccess
    public record DiscoveryInfo(boolean available, String message, List<ServiceInstanceInfo> instances) {

        public boolean hasInstances() {
            return !instances.isEmpty();
        }
    }

    /**
     * Safe service instance summary.
     *
     * @param id service instance id
     * @param uri masked service instance URI
     * @param status service instance status
     * @param zone service instance zone
     * @param region service instance region
     */
    @ReflectiveAccess
    public record ServiceInstanceInfo(String id, String uri, String status, String zone, String region) {
    }

    /**
     * Safe outbound filter metadata.
     *
     * @param type filter bean type
     * @param order filter order
     * @param patterns filter URL patterns
     * @param methods filter HTTP methods
     */
    @ReflectiveAccess
    public record FilterInfo(String type, String order, String patterns, String methods) {
    }
}
