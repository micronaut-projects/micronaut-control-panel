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
package io.micronaut.controlpanel.panels.jmx;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only diagnostics panel for Micronaut endpoint MBeans registered through JMX.
 */
@Singleton
@Requires(property = JmxControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class JmxControlPanel extends AbstractControlPanel<JmxDiagnostics> {

    public static final String NAME = "jmx";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("diagnostics", "Diagnostics", "fas fa-stethoscope", 30);

    private final JmxDiagnosticsService diagnosticsService;

    public JmxControlPanel(JmxDiagnosticsService diagnosticsService, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public JmxDiagnostics getBody() {
        return diagnosticsService.inspect();
    }

    @Override
    public String getBadge() {
        JmxDiagnostics body = getBody();
        if (!body.serverAvailable()) {
            return StringUtils.EMPTY_STRING;
        }
        return String.valueOf(body.endpointMBeanCount());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getDetailLinkName() {
        return "Inspect MBeans";
    }
}
