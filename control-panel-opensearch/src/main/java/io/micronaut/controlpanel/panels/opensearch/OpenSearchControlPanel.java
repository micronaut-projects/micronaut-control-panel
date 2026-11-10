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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.opensearch.conf.OpenSearchConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of the OpenSearch cluster the application uses: its hosts, health and indices.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = OpenSearchConfiguration.class)
@Requires(property = OpenSearchControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class OpenSearchControlPanel extends AbstractControlPanel<OpenSearchInfo> {

    public static final String NAME = "opensearch";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the datasource panels, so that OpenSearch is listed next to them.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final OpenSearchInspector inspector;

    /**
     * @param inspector the OpenSearch inspector
     * @param configuration the panel configuration
     */
    public OpenSearchControlPanel(OpenSearchInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    /**
     * Only lists the configured hosts, so that rendering the dashboard does not send requests to OpenSearch.
     *
     * @return the hosts, without cluster health or indices
     */
    @Override
    public OpenSearchInfo getBody() {
        return inspector.inspect(false);
    }

    /**
     * @return the hosts, cluster health and indices, for the detail view
     */
    @ReflectiveAccess
    public OpenSearchInfo getDetailedBody() {
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
