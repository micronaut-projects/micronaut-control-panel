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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.tracing.opentelemetry.inspector.InspectedSpan;
import io.micronaut.tracing.opentelemetry.inspector.InspectedTrace;
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * View models rendered by the tracing control panel, built from the trace inspector records.
 *
 * @since 2.3.0
 */
@Internal
public final class TraceViews {

    static final String EXCEPTION_EVENT = "exception";
    static final String EXCEPTION_TYPE = "exception.type";
    static final String EXCEPTION_MESSAGE = "exception.message";
    static final String EXCEPTION_STACKTRACE = "exception.stacktrace";

    private static final long NANOS_PER_MICRO = 1_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final double PERCENT = 100.0;

    private TraceViews() {
    }

    /**
     * Builds the table row of a trace summary.
     *
     * @param summary       the trace summary
     * @param configuration the panel configuration
     * @return the row
     */
    static TraceRow row(TraceSummary summary, TracingControlPanelConfiguration configuration) {
        String route = summary.httpRoute() != null ? summary.httpRoute() : summary.urlPath();
        return new TraceRow(
            summary.traceId(),
            summary.name(),
            summary.serviceName(),
            summary.httpMethod(),
            route,
            summary.httpStatus(),
            summary.error(),
            formatDuration(summary.durationNanos()),
            summary.durationNanos() / (double) NANOS_PER_MILLI,
            summary.spanCount(),
            summary.droppedSpanCount(),
            toInstant(summary.startEpochNanos()).toString(),
            summary.startEpochNanos() / NANOS_PER_MILLI,
            configuration.externalUrl(summary.traceId())
        );
    }

    /**
     * Builds the detail of a trace, with its spans ordered depth first and positioned relative to the trace start.
     *
     * @param trace         the trace
     * @param configuration the panel configuration
     * @return the detail
     */
    static TraceDetail detail(InspectedTrace trace, TracingControlPanelConfiguration configuration) {
        List<InspectedSpan> spans = trace.spans();
        long start = trace.summary().startEpochNanos();
        long end = start + trace.summary().durationNanos();
        for (InspectedSpan span : spans) {
            start = Math.min(start, span.startEpochNanos());
            end = Math.max(end, span.endEpochNanos());
        }
        long total = Math.max(1L, end - start);

        Set<String> spanIds = new LinkedHashSet<>();
        spans.forEach(span -> spanIds.add(span.spanId()));
        Map<String, List<InspectedSpan>> children = new HashMap<>();
        List<InspectedSpan> roots = new ArrayList<>();
        for (InspectedSpan span : spans) {
            String parent = span.parentSpanId();
            if (parent == null || !spanIds.contains(parent)) {
                roots.add(span);
            } else {
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(span);
            }
        }
        Comparator<InspectedSpan> byStart = Comparator.comparingLong(InspectedSpan::startEpochNanos);
        roots.sort(byStart);
        children.values().forEach(list -> list.sort(byStart));

        List<SpanRow> rows = new ArrayList<>(spans.size());
        Set<String> added = new HashSet<>();
        for (InspectedSpan root : roots) {
            addSpan(root, 0, start, total, children, added, rows);
        }
        // spans in a parent cycle are not reachable from a root
        for (InspectedSpan span : spans) {
            if (!added.contains(span.spanId())) {
                addSpan(span, 0, start, total, children, added, rows);
            }
        }
        List<Tick> ticks = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            ticks.add(new Tick(i * 25, formatDuration(total * i / 4)));
        }
        return new TraceDetail(row(trace.summary(), configuration), formatDuration(total), ticks, rows);
    }

    private static void addSpan(InspectedSpan span, int depth, long traceStart, long total,
                                Map<String, List<InspectedSpan>> children, Set<String> added, List<SpanRow> rows) {
        if (!added.add(span.spanId())) {
            return;
        }
        rows.add(spanRow(span, depth, traceStart, total));
        for (InspectedSpan child : children.getOrDefault(span.spanId(), List.of())) {
            addSpan(child, depth + 1, traceStart, total, children, added, rows);
        }
    }

    private static SpanRow spanRow(InspectedSpan span, int depth, long traceStart, long total) {
        long offset = span.startEpochNanos() - traceStart;
        List<EventRow> events = new ArrayList<>(span.events().size());
        List<ExceptionRow> exceptions = new ArrayList<>();
        for (InspectedSpan.Event event : span.events()) {
            if (EXCEPTION_EVENT.equals(event.name())) {
                Map<String, Object> attributes = event.attributes();
                exceptions.add(new ExceptionRow(
                    stringValue(attributes.get(EXCEPTION_TYPE)),
                    stringValue(attributes.get(EXCEPTION_MESSAGE)),
                    stringValue(attributes.get(EXCEPTION_STACKTRACE))
                ));
            } else {
                events.add(new EventRow(event.name(), formatDuration(event.epochNanos() - traceStart),
                    attributes(event.attributes())));
            }
        }
        List<LinkRow> links = span.links().stream()
            .map(link -> new LinkRow(link.traceId(), link.spanId(), attributes(link.attributes())))
            .toList();
        return new SpanRow(
            span.spanId(),
            span.parentSpanId(),
            span.name(),
            span.kind(),
            depth,
            percent(offset, total),
            percent(span.durationNanos(), total),
            formatDuration(offset),
            formatDuration(span.durationNanos()),
            span.status(),
            span.statusDescription(),
            span.isError() || !exceptions.isEmpty(),
            span.instrumentationScope(),
            attributes(span.attributes()),
            events,
            exceptions,
            links
        );
    }

    private static double percent(long value, long total) {
        double percent = value * PERCENT / total;
        return Math.round(Math.max(0, Math.min(PERCENT, percent)) * PERCENT) / PERCENT;
    }

    private static List<Attribute> attributes(Map<String, Object> attributes) {
        return new TreeMap<>(attributes).entrySet().stream()
            .map(entry -> new Attribute(entry.getKey(), stringValue(entry.getValue())))
            .toList();
    }

    private static @Nullable String stringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList().toString();
        }
        return String.valueOf(value);
    }

    private static Instant toInstant(long epochNanos) {
        return Instant.ofEpochSecond(epochNanos / NANOS_PER_SECOND, epochNanos % NANOS_PER_SECOND);
    }

    /**
     * Formats a duration in nanoseconds for display, for example {@code 850 µs}, {@code 12.4 ms} or {@code 1.25 s}.
     *
     * @param nanos the duration in nanoseconds
     * @return the formatted duration
     */
    static String formatDuration(long nanos) {
        if (nanos < NANOS_PER_MILLI) {
            return String.format(Locale.ROOT, "%d µs", Math.round(nanos / (double) NANOS_PER_MICRO));
        }
        if (nanos < NANOS_PER_SECOND) {
            return String.format(Locale.ROOT, "%.1f ms", nanos / (double) NANOS_PER_MILLI);
        }
        return String.format(Locale.ROOT, "%.2f s", nanos / (double) NANOS_PER_SECOND);
    }

    /**
     * A row of the recent traces table.
     *
     * @param traceId          the trace id
     * @param name             the root span name
     * @param serviceName      the service name
     * @param httpMethod       the HTTP method
     * @param route            the HTTP route, or the URL path when the route is not known
     * @param httpStatus       the HTTP response status
     * @param error            whether the trace has an error
     * @param duration         the formatted duration
     * @param durationMillis   the duration in milliseconds
     * @param spanCount        the number of retained spans
     * @param droppedSpanCount the number of dropped spans
     * @param start            the start time as an ISO-8601 instant
     * @param startEpochMillis the start time in milliseconds since the epoch
     * @param externalUrl      the URL of the trace in an external tracing UI
     */
    @Introspected
    @ReflectiveAccess
    public record TraceRow(String traceId,
                           String name,
                           @Nullable String serviceName,
                           @Nullable String httpMethod,
                           @Nullable String route,
                           @Nullable Integer httpStatus,
                           boolean error,
                           String duration,
                           double durationMillis,
                           int spanCount,
                           int droppedSpanCount,
                           String start,
                           long startEpochMillis,
                           @Nullable String externalUrl) {
    }

    /**
     * The traces matching a query.
     *
     * @param traces   the matching traces, newest first
     * @param retained the number of retained traces
     */
    @Introspected
    @ReflectiveAccess
    public record TraceList(List<TraceRow> traces, int retained) {
    }

    /**
     * A trace with its spans in waterfall order.
     *
     * @param summary  the trace row
     * @param duration the formatted duration covered by the waterfall
     * @param ticks    the time axis ticks
     * @param spans    the spans, depth first and ordered by start time
     */
    @Introspected
    @ReflectiveAccess
    public record TraceDetail(TraceRow summary, String duration, List<Tick> ticks, List<SpanRow> spans) {
    }

    /**
     * A tick of the waterfall time axis.
     *
     * @param percent the position, as a percentage of the trace duration
     * @param label   the formatted offset from the trace start
     */
    @Introspected
    @ReflectiveAccess
    public record Tick(int percent, String label) {
    }

    /**
     * A span of the waterfall.
     *
     * @param spanId               the span id
     * @param parentSpanId         the parent span id
     * @param name                 the span name
     * @param kind                 the span kind
     * @param depth                the nesting depth in the waterfall
     * @param offsetPercent        the start offset, as a percentage of the trace duration
     * @param widthPercent         the duration, as a percentage of the trace duration
     * @param offset               the formatted start offset from the trace start
     * @param duration             the formatted duration
     * @param status               the status code
     * @param statusDescription    the status description
     * @param error                whether the span has an error status or recorded an exception
     * @param instrumentationScope the instrumentation scope
     * @param attributes           the attributes, sorted by key
     * @param events               the events other than exceptions
     * @param exceptions           the recorded exceptions
     * @param links                the links
     */
    @Introspected
    @ReflectiveAccess
    public record SpanRow(String spanId,
                          @Nullable String parentSpanId,
                          String name,
                          String kind,
                          int depth,
                          double offsetPercent,
                          double widthPercent,
                          String offset,
                          String duration,
                          String status,
                          @Nullable String statusDescription,
                          boolean error,
                          String instrumentationScope,
                          List<Attribute> attributes,
                          List<EventRow> events,
                          List<ExceptionRow> exceptions,
                          List<LinkRow> links) {
    }

    /**
     * An attribute.
     *
     * @param key   the key
     * @param value the value
     */
    @Introspected
    @ReflectiveAccess
    public record Attribute(String key, @Nullable String value) {
    }

    /**
     * A span event other than an exception.
     *
     * @param name       the event name
     * @param offset     the formatted offset from the trace start
     * @param attributes the attributes, sorted by key
     */
    @Introspected
    @ReflectiveAccess
    public record EventRow(String name, String offset, List<Attribute> attributes) {
    }

    /**
     * An exception recorded on a span.
     *
     * @param type       the exception type
     * @param message    the exception message
     * @param stacktrace the stack trace
     */
    @Introspected
    @ReflectiveAccess
    public record ExceptionRow(@Nullable String type, @Nullable String message, @Nullable String stacktrace) {
    }

    /**
     * A span link.
     *
     * @param traceId    the linked trace id
     * @param spanId     the linked span id
     * @param attributes the attributes, sorted by key
     */
    @Introspected
    @ReflectiveAccess
    public record LinkRow(String traceId, String spanId, List<Attribute> attributes) {
    }
}
