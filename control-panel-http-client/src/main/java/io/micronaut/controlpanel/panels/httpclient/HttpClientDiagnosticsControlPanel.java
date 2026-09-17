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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Control panel that displays read-only diagnostics for Micronaut HTTP clients.
 */
@Singleton
@Refreshable
@Requires(classes = HttpClient.class)
@Requires(property = HttpClientDiagnosticsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
@SuppressWarnings("InjectMoreThanOneScopeAnnotationOnClass")
public class HttpClientDiagnosticsControlPanel extends AbstractControlPanel<HttpClientDiagnosticsControlPanel.Body> {

    public static final String NAME = "http-client";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("http", "HTTP", "fas fa-network-wired");

    private final HttpClientDiagnosticsService diagnosticsService;

    public HttpClientDiagnosticsControlPanel(HttpClientDiagnosticsService diagnosticsService,
                                             @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public Body getBody() {
        return new Body(diagnosticsService.collect());
    }

    @Override
    public String getBadge() {
        return String.valueOf(diagnosticsService.collect().clients().size());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    /**
     * The body of the HTTP client diagnostics panel.
     *
     * @param diagnostics collected diagnostics
     */
    @ReflectiveAccess
    public record Body(HttpClientDiagnostics diagnostics) {
    }
}
