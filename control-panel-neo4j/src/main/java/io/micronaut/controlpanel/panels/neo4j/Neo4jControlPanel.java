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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jBody;
import jakarta.inject.Named;
import org.neo4j.driver.Driver;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel for Neo4j driver diagnostics and schema metadata.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@EachBean(Driver.class)
public class Neo4jControlPanel extends AbstractEachBeanControlPanel<Neo4jBody> {

    public static final String NAME = "neo4j";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "Neo4j", "fas fa-circle-nodes");

    private final String beanName;
    private final Neo4jDiagnosticsService diagnosticsService;

    /**
     * Constructor.
     *
     * @param beanName the Neo4j driver bean name
     * @param driver the Neo4j driver
     * @param panelConfiguration the Neo4j panel configuration
     * @param connectionSummaryResolver safe connection summary resolver
     * @param configuration the control panel configuration
     */
    public Neo4jControlPanel(@Parameter String beanName,
                             @Parameter Driver driver,
                             Neo4jPanelConfiguration panelConfiguration,
                             @Parameter Neo4jConnectionSummaryResolver connectionSummaryResolver,
                             @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.diagnosticsService = new Neo4jDiagnosticsService(beanName, driver, panelConfiguration, connectionSummaryResolver);
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
    public Neo4jBody getBody() {
        return diagnosticsService.getBody();
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getIcon() {
        return "fas fa-circle-nodes";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }
}
