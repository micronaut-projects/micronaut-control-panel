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
package io.micronaut.controlpanel.panels.tracing;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracingControlPanelTest {

    private static final String TRACES = "/control-panel/tracing-control-panel-controller/traces";

    @Test
    void panelIsRegisteredWhenTheInspectorIsEnabled() {
        try (ApplicationContext ctx = start(Map.of())) {
            ControlPanel<?> panel = ctx.getBean(ControlPanelRepository.class).findByName(TracingControlPanel.NAME).orElseThrow();
            assertTrue(panel instanceof TracingControlPanel);
            assertEquals("Traces", panel.getTitle());
            assertEquals("fa-timeline", panel.getIcon());
            assertEquals(ControlPanel.Category.MAIN, panel.getCategory());
            assertTrue(ctx.containsBean(TracingController.class));
        }
    }

    @Test
    void panelIsAbsentWithoutTheInspector() {
        try (ApplicationContext ctx = start(Map.of("tracing.opentelemetry.inspector.enabled", false))) {
            assertFalse(ctx.containsBean(TraceInspector.class));
            assertFalse(ctx.containsBean(TracingControlPanel.class));
            assertFalse(ctx.containsBean(TracingController.class));
            assertTrue(ctx.getBean(ControlPanelRepository.class).findByName(TracingControlPanel.NAME).isEmpty());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext ctx = start(Map.of(TracingControlPanel.ENABLED_PROPERTY, false))) {
            assertTrue(ctx.containsBean(TraceInspector.class));
            assertFalse(ctx.containsBean(TracingControlPanel.class));
            assertFalse(ctx.containsBean(TracingController.class));
        }
    }

    @Test
    void bodyAndBadgeSummarizeTheRetainedTraces() {
        try (ApplicationContext ctx = start(Map.of())) {
            TracingControlPanel panel = ctx.getBean(TracingControlPanel.class);
            assertEquals(0, panel.getBody().retained());
            assertNull(panel.getBody().slowest());
            assertEquals("0", panel.getBadge());

            Tracer tracer = tracer(ctx);
            Instant now = Instant.now();
            seedBooks(tracer, now.minus(2, ChronoUnit.MINUTES));
            seedFailedOrder(tracer, now.minus(5, ChronoUnit.MINUTES));
            seedFailedOrder(tracer, now.minusSeconds(1));

            TracingControlPanel.Body body = panel.getBody();
            assertEquals(3, body.retained());
            assertEquals(2, body.errors());
            assertEquals(1, body.recentErrors());
            assertNotNull(body.slowest());
            assertEquals("/books", body.slowest().route());
            assertEquals("GET", body.slowest().httpMethod());
            assertEquals("120.0 ms", body.slowest().duration());
            assertEquals("2", panel.getBadge());
        }
    }

    @Test
    void controllerListsAndFiltersTraces() {
        try (ApplicationContext ctx = start(Map.of());
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = ctx.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();
            Tracer tracer = tracer(ctx);
            Instant now = Instant.now();
            String books = seedBooks(tracer, now.minusSeconds(10));
            String order = seedFailedOrder(tracer, now.minusSeconds(5));

            List<Map<String, Object>> all = traces(client, "");
            assertEquals(List.of(order, books), all.stream().map(row -> row.get("traceId")).toList());
            Map<String, Object> orderRow = all.get(0);
            assertEquals("POST /orders", orderRow.get("name"));
            assertEquals("POST", orderRow.get("httpMethod"));
            assertEquals("/orders", orderRow.get("route"));
            assertEquals(500, orderRow.get("httpStatus"));
            assertEquals(true, orderRow.get("error"));
            assertEquals("40.0 ms", orderRow.get("duration"));
            assertEquals(2, orderRow.get("spanCount"));
            assertFalse(orderRow.containsKey("externalUrl"));

            assertEquals(List.of(order), traces(client, "?errorsOnly=true").stream().map(row -> row.get("traceId")).toList());
            assertEquals(List.of(books), traces(client, "?name=BOOK").stream().map(row -> row.get("traceId")).toList());
            assertEquals(List.of(books), traces(client, "?minDurationMs=100").stream().map(row -> row.get("traceId")).toList());
            assertEquals(2, traces(client, "?minDurationMs=0&name=").size());

            HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(TRACES + "/00000000000000000000000000000001", Map.class));
            assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());

            HttpResponse<?> cleared = client.exchange(HttpRequest.DELETE(TRACES));
            assertEquals(HttpStatus.NO_CONTENT, cleared.getStatus());
            assertTrue(traces(client, "").isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void controllerRendersTheWaterfallOfATrace() {
        try (ApplicationContext ctx = start(Map.of("micronaut.control-panel.panels.tracing.external-url", "http://localhost:16686/trace/{traceId}"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = ctx.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();
            Tracer tracer = tracer(ctx);
            String books = seedBooks(tracer, Instant.now().minusSeconds(10));
            String order = seedFailedOrder(tracer, Instant.now().minusSeconds(5));

            Map<String, Object> detail = client.retrieve(HttpRequest.GET(TRACES + "/" + books), Argument.mapOf(String.class, Object.class));
            Map<String, Object> summary = (Map<String, Object>) detail.get("summary");
            assertEquals(books, summary.get("traceId"));
            assertEquals("http://localhost:16686/trace/" + books, summary.get("externalUrl"));
            assertEquals("120.0 ms", detail.get("duration"));
            List<Map<String, Object>> ticks = (List<Map<String, Object>>) detail.get("ticks");
            assertEquals(List.of(0, 25, 50, 75, 100), ticks.stream().map(tick -> tick.get("percent")).toList());
            assertEquals("60.0 ms", ticks.get(2).get("label"));

            List<Map<String, Object>> spans = (List<Map<String, Object>>) detail.get("spans");
            assertEquals(List.of("GET /books", "BookRepository.findAll", "SELECT books", "cache lookup"),
                spans.stream().map(span -> span.get("name")).toList());
            assertEquals(List.of(0, 1, 2, 1), spans.stream().map(span -> span.get("depth")).toList());
            assertEquals(List.of("SERVER", "INTERNAL", "CLIENT", "INTERNAL"), spans.stream().map(span -> span.get("kind")).toList());

            Map<String, Object> query = spans.get(2);
            assertEquals(spans.get(1).get("spanId"), query.get("parentSpanId"));
            assertEquals(25.0, ((Number) query.get("offsetPercent")).doubleValue());
            assertEquals(50.0, ((Number) query.get("widthPercent")).doubleValue());
            assertEquals("30.0 ms", query.get("offset"));
            assertEquals("60.0 ms", query.get("duration"));
            assertEquals(List.of(Map.of("key", "db.system", "value", "h2")), query.get("attributes"));
            List<Map<String, Object>> events = (List<Map<String, Object>>) query.get("events");
            assertEquals("rows fetched", events.get(0).get("name"));
            assertEquals("90.0 ms", events.get(0).get("offset"));
            assertEquals(List.of(Map.of("key", "rows", "value", "42")), events.get(0).get("attributes"));

            Map<String, Object> failed = client.retrieve(HttpRequest.GET(TRACES + "/" + order), Argument.mapOf(String.class, Object.class));
            List<Map<String, Object>> failedSpans = (List<Map<String, Object>>) failed.get("spans");
            Map<String, Object> handler = failedSpans.get(1);
            assertEquals("OrderController.create", handler.get("name"));
            assertEquals(true, handler.get("error"));
            assertEquals("ERROR", handler.get("status"));
            assertEquals("Out of stock", handler.get("statusDescription"));
            assertFalse(handler.containsKey("events"), "empty collections are omitted by default");
            List<Map<String, Object>> exceptions = (List<Map<String, Object>>) handler.get("exceptions");
            assertEquals(1, exceptions.size());
            assertEquals(IllegalStateException.class.getName(), exceptions.get(0).get("type"));
            assertEquals("Out of stock", exceptions.get(0).get("message"));
            assertTrue(((String) exceptions.get(0).get("stacktrace")).contains("TracingControlPanelTest"));
        }
    }

    @Test
    void cardAndDetailPagesRender() {
        try (ApplicationContext ctx = start(Map.of());
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = ctx.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();
            Tracer tracer = tracer(ctx);
            seedBooks(tracer, Instant.now().minusSeconds(10));
            seedFailedOrder(tracer, Instant.now().minusSeconds(5));

            String index = client.retrieve("/control-panel");
            assertTrue(index.contains("Traces"));
            assertTrue(index.contains("Traces retained"));
            assertTrue(index.contains("data-tracing-retained>2<"));
            assertTrue(index.contains("data-tracing-recent-errors>1<"));
            assertTrue(index.contains("/books"));
            assertTrue(index.contains("120.0 ms"));

            String detail = client.retrieve("/control-panel/tracing");
            assertTrue(detail.contains("Recent traces"));
            assertTrue(detail.contains("data-tracing-base=\"/control-panel/tracing-control-panel-controller\""));
            assertTrue(detail.contains("data-tracing-errors-only"));
            assertTrue(detail.contains("data-tracing-min-duration"));
            assertTrue(detail.contains("data-tracing-search"));
            assertTrue(detail.contains("data-tracing-auto-refresh"));
            assertTrue(detail.contains("data-tracing-clear"));
            assertTrue(detail.contains("data-tracing-waterfall"));
            assertTrue(detail.contains("data-tracing-span-panel"));
        }
    }

    private static ApplicationContext start(Map<String, Object> properties) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("tracing.opentelemetry.inspector.enabled", true);
        configuration.put("otel.traces.exporter", "none");
        configuration.put("micronaut.server.port", -1);
        configuration.putAll(properties);
        return ApplicationContext.builder(Environment.TEST).properties(configuration).start();
    }

    private static Tracer tracer(ApplicationContext ctx) {
        return ctx.getBean(OpenTelemetry.class).getTracer("control-panel-test");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> traces(BlockingHttpClient client, String query) {
        Map<String, Object> list = client.retrieve(HttpRequest.GET(TRACES + query), Argument.mapOf(String.class, Object.class));
        return (List<Map<String, Object>>) list.getOrDefault("traces", List.of());
    }

    /**
     * A 120 ms GET /books trace: a handler span with a nested query, and a cache lookup after it.
     */
    private static String seedBooks(Tracer tracer, Instant start) {
        Span root = tracer.spanBuilder("GET /books")
            .setSpanKind(SpanKind.SERVER)
            .setStartTimestamp(start)
            .setAttribute("http.request.method", "GET")
            .setAttribute("http.route", "/books")
            .setAttribute("url.path", "/books")
            .setAttribute("http.response.status_code", 200L)
            .startSpan();
        Span handler = tracer.spanBuilder("BookRepository.findAll")
            .setParent(Context.root().with(root))
            .setSpanKind(SpanKind.INTERNAL)
            .setStartTimestamp(start.plusMillis(10))
            .startSpan();
        Span query = tracer.spanBuilder("SELECT books")
            .setParent(Context.root().with(handler))
            .setSpanKind(SpanKind.CLIENT)
            .setStartTimestamp(start.plusMillis(30))
            .setAttribute("db.system", "h2")
            .startSpan();
        query.addEvent("rows fetched", Attributes.builder().put("rows", 42L).build(), start.plusMillis(90));
        query.end(start.plusMillis(90));
        handler.end(start.plusMillis(95));
        Span cache = tracer.spanBuilder("cache lookup")
            .setParent(Context.root().with(root))
            .setStartTimestamp(start.plusMillis(100))
            .startSpan();
        cache.end(start.plusMillis(110));
        root.end(start.plusMillis(120));
        return root.getSpanContext().getTraceId();
    }

    /**
     * A 40 ms POST /orders trace that fails with an exception.
     */
    private static String seedFailedOrder(Tracer tracer, Instant start) {
        Span root = tracer.spanBuilder("POST /orders")
            .setSpanKind(SpanKind.SERVER)
            .setStartTimestamp(start)
            .setAttribute("http.request.method", "POST")
            .setAttribute("http.route", "/orders")
            .setAttribute("http.response.status_code", 500L)
            .startSpan();
        Span handler = tracer.spanBuilder("OrderController.create")
            .setParent(Context.root().with(root))
            .setStartTimestamp(start.plusMillis(5))
            .startSpan();
        handler.recordException(new IllegalStateException("Out of stock"));
        handler.setStatus(StatusCode.ERROR, "Out of stock");
        handler.end(start.plusMillis(35));
        root.setStatus(StatusCode.ERROR);
        root.end(start.plusMillis(40));
        return root.getSpanContext().getTraceId();
    }
}
