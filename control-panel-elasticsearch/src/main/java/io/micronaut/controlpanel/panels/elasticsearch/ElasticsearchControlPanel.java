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
package io.micronaut.controlpanel.panels.elasticsearch;

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Control panel for read-only Elasticsearch cluster and index diagnostics.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@Singleton
public class ElasticsearchControlPanel extends AbstractControlPanel<ElasticsearchDiagnostics> {

    public static final String NAME = "elasticsearch";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("search", "Search", "fas fa-magnifying-glass-chart");

    private final ElasticsearchDiagnosticsService diagnosticsService;

    public ElasticsearchControlPanel(ElasticsearchDiagnosticsService diagnosticsService,
                                     @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public ElasticsearchDiagnostics getBody() {
        return diagnosticsService.diagnostics();
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getDetailLinkName() {
        return "Inspect";
    }
}
