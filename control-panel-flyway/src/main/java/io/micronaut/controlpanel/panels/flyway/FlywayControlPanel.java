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
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.flyway.FlywayConfigurationProperties;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Read-only view of the Flyway migration history of each datasource configured under {@code flyway.datasources}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = FlywayConfigurationProperties.class)
@Requires(property = FlywayControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class FlywayControlPanel extends AbstractControlPanel<FlywayControlPanel.Body> {

    public static final String NAME = "flyway";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the datasource panels, so that Flyway is listed next to them.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final FlywayInspector inspector;

    /**
     * @param inspector the Flyway inspector
     * @param configuration the panel configuration
     */
    public FlywayControlPanel(FlywayInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public Body getBody() {
        return new Body(inspector.inspect());
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }

    @Override
    public String getDetailLinkName() {
        return "Migrations";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    /**
     * The Flyway configurations. Internal to the Flyway panel views.
     *
     * @param schemas one entry per Flyway configuration, sorted by name
     */
    @ReflectiveAccess
    public record Body(List<FlywaySchema> schemas) {
    }
}
