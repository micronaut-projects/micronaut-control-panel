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

import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * Body rendered by the tracing diagnostics panel.
 *
 * @param providers detected tracing providers
 * @param exporters exporter rows grouped by signal
 * @param resourceAttributes OpenTelemetry resource attributes
 * @param instrumentations instrumentation surface rows
 * @param configuration recognized tracing configuration values
 * @param diagnostics local deterministic diagnostics
 * @param serviceName effective service name
 * @param sampler configured sampler
 * @param propagators configured propagators
 * @param providerState provider summary for compact rendering
 * @param presentInstrumentationCount number of present instrumentation rows
 */
@ReflectiveAccess
@Internal
public record TracingBody(
        List<TracingProviderInfo> providers,
        List<TracingExporterInfo> exporters,
        List<TracingKeyValue> resourceAttributes,
        List<TracingInstrumentationInfo> instrumentations,
        List<TracingKeyValue> configuration,
        List<TracingDiagnostic> diagnostics,
        String serviceName,
        String sampler,
        String propagators,
        String providerState,
        int presentInstrumentationCount
) {

    /**
     * Whether tracing providers were detected.
     *
     * @return true when at least one tracing provider was detected
     */
    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    /**
     * Whether resource attributes are available.
     *
     * @return true when at least one resource attribute is available
     */
    public boolean hasResourceAttributes() {
        return !resourceAttributes.isEmpty();
    }

    /**
     * Whether local diagnostics are available.
     *
     * @return true when at least one local diagnostic is available
     */
    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }
}
