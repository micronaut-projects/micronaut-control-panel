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
package io.micronaut.controlpanel.panels.neo4j;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.neo4j.bolt.Neo4jBoltConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of the Neo4j server the application uses: its URI, version, databases and graph size.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = Neo4jBoltConfiguration.class)
@Requires(property = Neo4jControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class Neo4jControlPanel extends AbstractControlPanel<Neo4jInfo> {

    public static final String NAME = "neo4j";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the datasource panels, so that Neo4j is listed next to them.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final Neo4jInspector inspector;

    /**
     * @param inspector the Neo4j inspector
     * @param configuration the panel configuration
     */
    public Neo4jControlPanel(Neo4jInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    /**
     * Only reads the configuration, so that rendering the dashboard does not send queries to Neo4j.
     *
     * @return the URI, without server details
     */
    @Override
    public Neo4jInfo getBody() {
        return inspector.inspect(false);
    }

    /**
     * @return the URI, server version, databases and graph size, for the detail view
     */
    @ReflectiveAccess
    public Neo4jInfo getDetailedBody() {
        return inspector.inspect(true);
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }
}
