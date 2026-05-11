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
package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.crac.OrderedResource;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only CRaC lifecycle diagnostics control panel.
 */
@Singleton
@Requires(classes = OrderedResource.class)
@Requires(property = CracControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class CracControlPanel extends AbstractControlPanel<CracDiagnostics> {

    public static final String NAME = "crac";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final Category CATEGORY = new Category("runtime", "Runtime", "fa-microchip");

    private final CracDiagnosticsService diagnosticsService;

    public CracControlPanel(CracDiagnosticsService diagnosticsService,
                            @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public CracDiagnostics getBody() {
        return diagnosticsService.diagnostics();
    }

    @Override
    public String getBadge() {
        CracDiagnostics diagnostics = diagnosticsService.diagnostics();
        return String.valueOf(diagnostics.resources().size());
    }

    @Override
    public Category getCategory() {
        return CATEGORY;
    }
}
