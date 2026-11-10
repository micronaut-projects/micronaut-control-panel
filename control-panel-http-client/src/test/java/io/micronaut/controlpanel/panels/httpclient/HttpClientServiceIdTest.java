package io.micronaut.controlpanel.panels.httpclient;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientServiceIdTest {

    private static final String SPEC = "HttpClientServiceIdTest";

    @Test
    void camelCaseClientIdsShareTheRowOfTheirHyphenatedServiceConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC,
            "micronaut.http.services.orders-service.url", "https://orders.example.com"
        ))) {
            List<HttpClientInfo> clients = context.getBean(HttpClientInspector.class).inspect();

            assertEquals(List.of("orders-service", "payments-service"), clients.stream().map(HttpClientInfo::id).toList());
            HttpClientInfo orders = clients.get(0);
            assertTrue(orders.configured());
            assertEquals(List.of(OrdersClient.class.getName()), orders.declarativeTypes());
            assertEquals(List.of("https://orders.example.com"), orders.urls());
            assertFalse(orders.unresolved());
            assertFalse(orders.resolvedByDiscoveryClient());

            HttpClientInfo payments = clients.get(1);
            assertTrue(payments.unresolved());
            assertFalse(payments.resolvedByDiscoveryClient());
            assertEquals(1, new HttpClientsControlPanel.Body(clients).unresolvedCount());
        }
    }

    @Test
    void clientsWithoutAUrlAreLeftToADiscoveryClient() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC,
            "discovery-client", "true"
        ))) {
            List<HttpClientInfo> clients = context.getBean(HttpClientInspector.class).inspect();

            HttpClientInfo payments = clients.stream().filter(client -> client.id().equals("payments-service")).findFirst().orElseThrow();
            assertFalse(payments.unresolved());
            assertTrue(payments.resolvedByDiscoveryClient());
            assertEquals(0, new HttpClientsControlPanel.Body(clients).unresolvedCount());
            assertFalse(context.getBean(TestDiscoveryClient.class).queried);
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client("ordersService")
    interface OrdersClient {
        @Get("/orders")
        String orders();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client("paymentsService")
    interface PaymentsClient {
        @Get("/payments")
        String payments();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "discovery-client", value = "true")
    @Singleton
    static class TestDiscoveryClient implements DiscoveryClient {

        boolean queried;

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            queried = true;
            return subscriber -> { };
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            queried = true;
            return subscriber -> { };
        }

        @Override
        public String getDescription() {
            return "test";
        }

        @Override
        public void close() {
            // nothing to close
        }
    }
}
