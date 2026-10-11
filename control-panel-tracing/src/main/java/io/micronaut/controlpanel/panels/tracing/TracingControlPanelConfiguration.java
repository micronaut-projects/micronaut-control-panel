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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the tracing control panel, in addition to the common {@link ControlPanelConfiguration} properties.
 *
 * @since 2.3.0
 */
@ConfigurationProperties(TracingControlPanelConfiguration.PREFIX)
public class TracingControlPanelConfiguration {

    public static final String PREFIX = ControlPanelConfiguration.PREFIX + "." + TracingControlPanel.NAME;
    public static final int DEFAULT_LIMIT = 100;
    public static final String TRACE_ID_PLACEHOLDER = "{traceId}";

    private @Nullable String externalUrl;
    private int limit = DEFAULT_LIMIT;

    /**
     * @return the URL template used to open a trace in an external tracing UI, or {@code null}
     */
    public @Nullable String getExternalUrl() {
        return externalUrl;
    }

    /**
     * URL template used to open a trace in an external tracing UI such as Jaeger, Tempo or Zipkin. The
     * {@value #TRACE_ID_PLACEHOLDER} placeholder is replaced with the trace id, for example
     * {@code http://localhost:16686/trace/{traceId}}. Default value: none.
     *
     * @param externalUrl the URL template
     */
    public void setExternalUrl(@Nullable String externalUrl) {
        this.externalUrl = externalUrl;
    }

    /**
     * @return the maximum number of traces listed by the detail view
     */
    public int getLimit() {
        return limit;
    }

    /**
     * The maximum number of traces listed by the detail view. Default value: {@value #DEFAULT_LIMIT}.
     *
     * @param limit the maximum number of traces
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * Resolves the external URL of a trace.
     *
     * @param traceId the trace id
     * @return the URL, or {@code null} if no external URL template is configured
     */
    public @Nullable String externalUrl(String traceId) {
        if (externalUrl == null || externalUrl.isBlank()) {
            return null;
        }
        return externalUrl.replace(TRACE_ID_PLACEHOLDER, traceId);
    }
}
