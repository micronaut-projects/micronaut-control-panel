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
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.tracing.opentelemetry.inspector.TraceInspector;
import io.micronaut.tracing.opentelemetry.inspector.TraceQuery;
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Control panel that shows the recent traces retained by the Micronaut Tracing {@link TraceInspector}.
 *
 * @since 2.3.0
 */
@Singleton
@Requires(beans = TraceInspector.class)
public final class TracingControlPanel extends AbstractControlPanel<TracingControlPanel.Body> {

    public static final String NAME = "tracing";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private static final Duration RECENT_ERRORS_WINDOW = Duration.ofMinutes(1);

    private final TraceInspector inspector;
    private final TracingControlPanelConfiguration tracingConfiguration;

    /**
     * @param configuration        the common panel configuration
     * @param tracingConfiguration the tracing panel configuration
     * @param inspector            the trace inspector
     */
    public TracingControlPanel(@Named(NAME) ControlPanelConfiguration configuration,
                               TracingControlPanelConfiguration tracingConfiguration,
                               TraceInspector inspector) {
        super(NAME, configuration);
        this.inspector = inspector;
        this.tracingConfiguration = tracingConfiguration;
    }

    @Override
    public Body getBody() {
        List<TraceSummary> traces = inspector.traces();
        long errors = traces.stream().filter(TraceSummary::error).count();
        int recentErrors = inspector.traces(TraceQuery.builder()
            .errorsOnly(true)
            .since(Instant.now().minus(RECENT_ERRORS_WINDOW))
            .build()).size();
        TraceViews.TraceRow slowest = traces.stream()
            .max(Comparator.comparingLong(TraceSummary::durationNanos))
            .map(summary -> TraceViews.row(summary, tracingConfiguration))
            .orElse(null);
        return new Body(traces.size(), (int) errors, recentErrors, slowest,
            ControlPanelSecurityPaths.TRACING_PATH, tracingConfiguration.getLimit());
    }

    /**
     * @return the number of retained traces with an error
     */
    @Override
    public String getBadge() {
        return String.valueOf(inspector.traces(TraceQuery.builder().errorsOnly(true).build()).size());
    }

    /**
     * Summary of the retained traces.
     *
     * @param retained       the number of retained traces
     * @param errors         the number of retained traces with an error
     * @param recentErrors   the number of traces with an error started in the last minute
     * @param slowest        the slowest retained trace
     * @param controllerPath the path of the JSON helper routes, relative to the control panel path
     * @param limit          the maximum number of traces listed by the detail view
     */
    @ReflectiveAccess
    public record Body(int retained,
                       int errors,
                       int recentErrors,
                       TraceViews.@Nullable TraceRow slowest,
                       String controllerPath,
                       int limit) {
    }
}
