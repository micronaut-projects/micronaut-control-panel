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
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Control panel for local tracing diagnostics.
 */
@Singleton
@Requires(property = TracingControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class TracingControlPanel extends AbstractControlPanel<TracingBody> {

    /**
     * The tracing control panel name.
     */
    public static final String NAME = "tracing";

    /**
     * Property used to enable or disable the tracing panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * Observability category used by the tracing panel.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("observability", "Observability", "fa-chart-line", 20);

    private final TracingDiagnosticsService diagnosticsService;

    /**
     * Creates the tracing control panel.
     *
     * @param diagnosticsService tracing diagnostics service
     * @param configuration control panel configuration
     */
    public TracingControlPanel(TracingDiagnosticsService diagnosticsService,
                               @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public TracingBody getBody() {
        return diagnosticsService.getBody();
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getBadge() {
        TracingBody body = getBody();
        return body.hasProviders() ? body.providers().size() + " providers" : "not detected";
    }

    @Override
    public String getDetailLinkName() {
        return "Inspect tracing";
    }
}
