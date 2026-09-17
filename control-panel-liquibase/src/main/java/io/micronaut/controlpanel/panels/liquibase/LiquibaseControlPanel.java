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
package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.liquibase.LiquibaseConfigurationProperties;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Read-only Liquibase change-set history control panel.
 */
@Singleton
@Requires(classes = LiquibaseConfigurationProperties.class)
@Requires(property = LiquibaseControlPanel.CONFIGURATION_PREFIX + ".enabled", notEquals = "false")
public class LiquibaseControlPanel extends AbstractControlPanel<LiquibasePanelBody> {

    public static final String NAME = "liquibase";
    public static final String CONFIGURATION_PREFIX = ControlPanelModuleConfiguration.PREFIX + ".panels." + NAME;
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final LiquibaseHistoryService historyService;

    public LiquibaseControlPanel(LiquibaseHistoryService historyService,
                                 @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.historyService = historyService;
    }

    @Override
    public LiquibasePanelBody getBody() {
        return historyService.getBody();
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }
}
