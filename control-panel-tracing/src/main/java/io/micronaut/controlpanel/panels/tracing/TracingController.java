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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector;
import io.micronaut.tracing.opentelemetry.inspector.TraceQuery;
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Comparator;

/**
 * JSON routes used by the tracing control panel detail view.
 *
 * @since 2.3.0
 */
@Controller(ControlPanelSecurityPaths.TRACING)
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
@Requires(beans = TraceInspector.class)
public final class TracingController {

    private final TraceInspector inspector;
    private final TracingControlPanelConfiguration configuration;

    TracingController(TraceInspector inspector, TracingControlPanelConfiguration configuration) {
        this.inspector = inspector;
        this.configuration = configuration;
    }

    /**
     * Lists the retained traces matching the filters, most recently started first.
     *
     * @param name          text that the root span name, HTTP route or URL path contains, ignoring case
     * @param errorsOnly    whether to list only traces with an error
     * @param minDurationMs the minimum trace duration, in milliseconds
     * @return the matching traces
     */
    @Get("/traces{?name,errorsOnly,minDurationMs}")
    public TraceViews.TraceList traces(@Nullable @QueryValue String name,
                                       @QueryValue(defaultValue = "false") boolean errorsOnly,
                                       @Nullable @QueryValue Long minDurationMs) {
        TraceQuery query = TraceQuery.builder()
            .name(name == null || name.isBlank() ? null : name.trim())
            .errorsOnly(errorsOnly)
            .minDuration(minDurationMs == null || minDurationMs <= 0 ? null : Duration.ofMillis(minDurationMs))
            .limit(configuration.getLimit())
            .build();
        var traces = inspector.traces(query).stream()
            .sorted(Comparator.comparingLong(TraceSummary::startEpochNanos).reversed())
            .map(summary -> TraceViews.row(summary, configuration))
            .toList();
        return new TraceViews.TraceList(traces, inspector.traces().size());
    }

    /**
     * Returns a retained trace with its spans in waterfall order.
     *
     * @param traceId the trace id
     * @return the trace, or 404 if it is not retained
     */
    @Get("/traces/{traceId}")
    public HttpResponse<TraceViews.TraceDetail> trace(String traceId) {
        return inspector.trace(traceId)
            .map(trace -> HttpResponse.ok(TraceViews.detail(trace, configuration)))
            .orElseGet(HttpResponse::notFound);
    }

    /**
     * Discards all retained traces.
     *
     * @return 204
     */
    @Delete("/traces")
    public HttpResponse<Void> clear() {
        inspector.clear();
        return HttpResponse.noContent();
    }
}
