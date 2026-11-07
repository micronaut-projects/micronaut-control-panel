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
package io.micronaut.controlpanel.panels.mongodb;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Read-only view of the MongoDB clients of the application: the servers each one sees, and their databases and
 * collections.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = com.mongodb.connection.ClusterDescription.class)
@Requires(property = MongoDbControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class MongoDbControlPanel extends AbstractControlPanel<MongoDbControlPanel.Body> {

    public static final String NAME = "mongodb";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the datasource panels, so that MongoDB is listed next to them.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final MongoDbInspector inspector;

    /**
     * @param inspector the MongoDB inspector
     * @param configuration the panel configuration
     */
    public MongoDbControlPanel(MongoDbInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    /**
     * Only lists the servers each client sees, which the driver keeps in memory, so that rendering the dashboard does
     * not query MongoDB.
     *
     * @return the clients, without databases
     */
    @Override
    public Body getBody() {
        return new Body(inspector.inspect(false));
    }

    /**
     * @return the clients, with their databases and collections, for the detail view
     */
    @ReflectiveAccess
    public Body getDetailedBody() {
        return new Body(inspector.inspect(true));
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    /**
     * The MongoDB clients. Internal to the MongoDB panel views.
     *
     * @param clients the clients, sorted by name
     */
    @ReflectiveAccess
    public record Body(List<MongoClientInfo> clients) {
    }
}
