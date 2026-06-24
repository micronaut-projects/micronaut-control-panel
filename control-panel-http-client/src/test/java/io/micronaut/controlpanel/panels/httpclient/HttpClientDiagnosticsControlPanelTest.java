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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.discovery.StaticServiceInstanceList;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Map.entry;

final class HttpClientDiagnosticsControlPanelTest {

    @Test
    void collectsFixedUrlAndServiceIdClientsWithSafeConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            entry("micronaut.http.services.orders.url", "https://user:secret@example.com:8443/api?api_key=dev-secret#access-token"),
            entry("micronaut.http.services.orders.path", "/v1"),
            entry("micronaut.http.services.orders.connect-timeout", "2s"),
            entry("micronaut.http.services.orders.read-timeout", "3s"),
            entry("micronaut.http.services.orders.request-timeout", "4s"),
            entry("micronaut.http.services.orders.proxy-type", "HTTP"),
            entry("micronaut.http.services.orders.proxy-address", "localhost:8080"),
            entry("micronaut.http.services.orders.proxy-username", "proxy-user"),
            entry("micronaut.http.services.orders.proxy-password", "proxy-secret"),
            entry("micronaut.http.services.orders.ssl.enabled", true),
            entry("micronaut.http.services.orders.ssl.key-store.password", "changeit")
        ))) {
            HttpClientDiagnostics diagnostics = context.getBean(HttpClientDiagnosticsService.class).collect();

            HttpClientDiagnostics.HttpClientInfo orders = find(diagnostics, "orders");
            assertEquals("Fixed URL", orders.target().kind());
            assertFalse(orders.target().value().contains("user:secret"));
            assertFalse(orders.target().value().contains("dev-secret"));
            assertFalse(orders.target().value().contains("access-token"));
            assertTrue(orders.target().value().contains("Masked"));
            assertEquals("/v1", orders.configuration().path());
            assertEquals("PT2S", orders.configuration().connectTimeout());
            assertEquals("PT3S", orders.configuration().readTimeout());
            assertEquals("PT4S", orders.configuration().requestTimeout());
            assertEquals("Masked", orders.configuration().proxyCredentials());
            assertTrue(orders.configuration().tls().contains("key/trust material=masked"));
            assertTrue(orders.hasFilters());

            HttpClientDiagnostics.HttpClientInfo inventory = find(diagnostics, "inventory");
            assertEquals("Service ID", inventory.target().kind());
            assertTrue(inventory.discovery().available());
            assertEquals(1, inventory.discovery().instances().size());
            assertEquals("https://inventory.example.test/api?Masked#Masked", inventory.discovery().instances().getFirst().uri());
            assertFalse(inventory.discovery().instances().getFirst().uri().contains("instance-secret"));
            assertFalse(inventory.discovery().instances().getFirst().uri().contains("fragment-secret"));
        }
    }

    @Test
    void rendersUnavailableDiscoveryStateWhenNoInstancesAreVisible() {
        try (ApplicationContext context = ApplicationContext.run()) {
            HttpClientDiagnostics.HttpClientInfo payments = find(context.getBean(HttpClientDiagnosticsService.class).collect(), "payments");

            assertEquals("Service ID", payments.target().kind());
            assertFalse(payments.discovery().available());
            assertTrue(payments.discovery().message().contains("No application-visible service instances"));
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(HttpClientDiagnosticsControlPanel.ENABLED_PROPERTY, false))) {
            assertFalse(context.containsBean(HttpClientDiagnosticsControlPanel.class));
        }
    }

    @Test
    void panelBodyExposesCollectedDiagnostics() {
        try (ApplicationContext context = ApplicationContext.run()) {
            HttpClientDiagnosticsControlPanel panel = context.getBean(HttpClientDiagnosticsControlPanel.class);

            assertNotNull(panel.getBody().diagnostics());
            assertTrue(Integer.parseInt(panel.getBadge()) >= 2);
        }
    }

    private static HttpClientDiagnostics.HttpClientInfo find(HttpClientDiagnostics diagnostics, String id) {
        return diagnostics.clients().stream()
            .filter(client -> id.equals(client.id()))
            .findFirst()
            .orElseThrow();
    }

    @Client("orders")
    interface OrdersClient {
    }

    @Client("inventory")
    interface InventoryClient {
    }

    @Client("payments")
    interface PaymentsClient {
    }

    @Factory
    static class TestDiscoveryFactory {
        @Singleton
        @Requires(missingProperty = "disable.inventory.instances")
        ServiceInstanceList inventoryInstances() {
            return new StaticServiceInstanceList("inventory", List.of(URI.create("https://inventory.example.test/api?token=instance-secret#fragment-secret")));
        }
    }

    @Filter(serviceId = "orders")
    static class OrdersFilter implements HttpClientFilter {
        @Override
        public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return chain.proceed(request);
        }
    }
}
