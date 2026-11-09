package io.micronaut.controlpanel.panels.httpclient;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientInspectorTest {

    private static final String SPEC = "HttpClientInspectorTest";

    @Test
    void listsDeclarativeConfiguredAndDiscoveredClients() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC,
            "micronaut.http.services.billing.url", "https://user:secret@billing.example.com:8443/api?token=abc#frag",
            "micronaut.http.services.billing.read-timeout", "15s",
            "micronaut.http.services.billing.connect-timeout", "250ms",
            "micronaut.http.services.billing.path", "/v2"
        ))) {
            List<HttpClientInfo> clients = context.getBean(HttpClientInspector.class).inspect();

            assertEquals(List.of("billing", "https://catalog.example.com", "inventory", "same server", "unknown"),
                clients.stream().map(HttpClientInfo::id).toList());

            HttpClientInfo billing = find(clients, "billing");
            assertTrue(billing.configured());
            assertEquals(List.of(BillingClient.class.getName()), billing.declarativeTypes());
            assertEquals(List.of("https://billing.example.com:8443/api"), billing.urls());
            assertEquals("/v2", billing.path());
            assertEquals("250ms", billing.connectTimeout());
            assertEquals("15s", billing.readTimeout());
            assertFalse(billing.discovered());
            assertFalse(billing.unresolved());
            assertEquals(List.of(BillingFilter.class.getName()), billing.filters());

            HttpClientInfo catalog = find(clients, "https://catalog.example.com");
            assertFalse(catalog.configured());
            assertEquals(List.of("https://catalog.example.com"), catalog.urls());
            assertEquals("/catalog", catalog.path());
            assertNull(catalog.connectTimeout());
            assertTrue(catalog.filters().isEmpty());

            HttpClientInfo inventory = find(clients, "inventory");
            assertTrue(inventory.discovered());
            assertEquals(List.of(new HttpClientInfo.Instance("http://10.0.0.1:8080", "UP")), inventory.instances());

            HttpClientInfo sameServer = find(clients, HttpClientInspector.SAME_SERVER);
            assertEquals(List.of(SameServerClient.class.getName()), sameServer.declarativeTypes());
            assertFalse(sameServer.unresolved());
            assertTrue(sameServer.filters().isEmpty());

            HttpClientInfo unknown = find(clients, "unknown");
            assertTrue(unknown.unresolved());
            assertNull(unknown.instances());
            assertEquals(List.of(UnknownAdminClient.class.getName(), UnknownClient.class.getName()), unknown.declarativeTypes());
            assertEquals("/admin, /public", unknown.path());
            assertEquals(List.of(AuditFilter.class.getName()), unknown.filters());
        }
    }

    @Test
    void sanitizeRemovesCredentials() {
        assertEquals("https://example.com/a", HttpClientInspector.sanitize("https://u:p@example.com/a?x=y#z"));
        assertEquals("http://example.com:8080", HttpClientInspector.sanitize("http://example.com:8080"));
        assertEquals("(hidden)", HttpClientInspector.sanitize("not a uri with spaces"));
        assertEquals("http://my_service:8080/api", HttpClientInspector.sanitize("http://user:secret@my_service:8080/api"));
        assertEquals("http://my_service:8080/api", HttpClientInspector.sanitize("http://my_service:8080/api?token=x"));
        assertEquals("https://host:9200", HttpClientInspector.sanitize("https://user:p@ss@host:9200"));
        assertEquals("https://host:9200/a@b", HttpClientInspector.sanitize("https://host:9200/a@b?k=v"));
    }

    private static HttpClientInfo find(List<HttpClientInfo> clients, String id) {
        return clients.stream().filter(client -> client.id().equals(id)).findFirst().orElseThrow();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client(id = "billing")
    interface BillingClient {
        @Get("/invoices")
        String invoices();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client(value = "https://user:secret@catalog.example.com?key=abc", path = "/catalog")
    interface CatalogClient {
        @Get
        String items();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client(value = "unknown", path = "/public")
    interface UnknownClient {
        @Get
        String get();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client(value = "unknown", path = "/admin")
    @Audited
    interface UnknownAdminClient {
        @Get
        String get();
    }

    @FilterMatcher
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Audited {
    }

    @Requires(property = "spec.name", value = SPEC)
    @Audited
    @Singleton
    static class AuditFilter implements HttpClientFilter {
        @Override
        public org.reactivestreams.Publisher<? extends io.micronaut.http.HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return chain.proceed(request);
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client("/")
    interface SameServerClient {
        @Get("/ping")
        String ping();
    }

    @Requires(property = "spec.name", value = SPEC)
    @ClientFilter(serviceId = "billing")
    static class BillingFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            request.header("X-Test", "1");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    static class InventoryInstances implements ServiceInstanceList {
        @Override
        public String getID() {
            return "inventory";
        }

        @Override
        public @NonNull List<ServiceInstance> getInstances() {
            return List.of(ServiceInstance.of("inventory", URI.create("http://user:pw@10.0.0.1:8080?secret=1")));
        }
    }
}
