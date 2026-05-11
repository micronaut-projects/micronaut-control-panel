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
package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.flyway.model.FlywayBody;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.flywaydb.core.Flyway;

/**
 * Read-only Flyway migrations status control panel.
 */
@Singleton
@Requires(classes = Flyway.class)
public class FlywayControlPanel extends AbstractControlPanel<FlywayBody> {

    /**
     * Flyway panel name.
     */
    public static final String NAME = "flyway";
    /**
     * Data Sources category used by the Flyway panel.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final FlywayStatusService flywayStatusService;

    /**
     * Creates a Flyway control panel.
     *
     * @param flywayStatusService status service
     * @param configuration panel configuration
     */
    public FlywayControlPanel(FlywayStatusService flywayStatusService,
                              @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.flywayStatusService = flywayStatusService;
    }

    @Override
    public FlywayBody getBody() {
        return flywayStatusService.getBody();
    }

    @Override
    public String getBadge() {
        return String.valueOf(getBody().totalConfigurations());
    }

    @Override
    public String getDetailLinkName() {
        return "Migrations";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }
}
