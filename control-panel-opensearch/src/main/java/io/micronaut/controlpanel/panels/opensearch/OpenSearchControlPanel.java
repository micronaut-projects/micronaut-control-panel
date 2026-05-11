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
package io.micronaut.controlpanel.panels.opensearch;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.opensearch.model.OpenSearchDiagnostics;
import jakarta.inject.Named;

/**
 * Control panel for read-only OpenSearch diagnostics.
 */
@EachBean(OpenSearchDiagnosticsService.class)
public class OpenSearchControlPanel extends AbstractEachBeanControlPanel<OpenSearchDiagnostics> {

    public static final String NAME = "opensearch";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final String DEFAULT_ICON_CLASS = "fa-magnifying-glass-chart";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "OpenSearch", DEFAULT_ICON_CLASS);

    private final String beanName;
    private final OpenSearchDiagnosticsService diagnosticsService;

    public OpenSearchControlPanel(@Parameter String beanName,
                                  @Parameter OpenSearchDiagnosticsService diagnosticsService,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    protected String getBeanName() {
        return beanName;
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public OpenSearchDiagnostics getBody() {
        return diagnosticsService.diagnostics();
    }

    @Override
    public String getBadge() {
        return getBody().statusLabel();
    }

    @Override
    public String getDetailLinkName() {
        return "Diagnostics";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }
}
