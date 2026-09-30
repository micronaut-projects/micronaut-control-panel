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
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jBody;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.neo4j.driver.Driver;

/**
 * Empty-state panel shown when the Neo4j module is present but no driver beans exist.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@Singleton
@Requires(missingBeans = Driver.class)
public class Neo4jNoDriversControlPanel extends AbstractControlPanel<Neo4jBody> {

    /**
     * Constructor.
     *
     * @param configuration the control panel configuration
     */
    public Neo4jNoDriversControlPanel(@Named(Neo4jControlPanel.NAME) ControlPanelConfiguration configuration) {
        super(Neo4jControlPanel.NAME, configuration);
    }

    @Override
    public Neo4jBody getBody() {
        return Neo4jBody.noDrivers();
    }

    @Override
    public String getName() {
        return Neo4jControlPanel.NAME;
    }

    @Override
    public String getTitle() {
        return "Neo4j";
    }

    @Override
    public String getIcon() {
        return "fas fa-circle-nodes";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return Neo4jControlPanel.CATEGORY;
    }

    @Override
    public boolean hasDetails() {
        return false;
    }
}
