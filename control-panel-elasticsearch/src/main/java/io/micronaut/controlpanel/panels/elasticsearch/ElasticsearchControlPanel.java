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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.elasticsearch.DefaultElasticsearchConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of the Elasticsearch cluster the application uses: its hosts, health and indices.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = DefaultElasticsearchConfiguration.class)
@Requires(property = ElasticsearchControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ElasticsearchControlPanel extends AbstractControlPanel<ElasticsearchInfo> {

    public static final String NAME = "elasticsearch";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the datasource panels, so that Elasticsearch is listed next to them.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("datasource", "Data Sources", "fas fa-database");

    private final ElasticsearchInspector inspector;

    /**
     * @param inspector the Elasticsearch inspector
     * @param configuration the panel configuration
     */
    public ElasticsearchControlPanel(ElasticsearchInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    /**
     * Only lists the configured hosts, so that rendering the dashboard does not send requests to Elasticsearch.
     *
     * @return the hosts, without cluster health or indices
     */
    @Override
    public ElasticsearchInfo getBody() {
        return inspector.inspect(false);
    }

    /**
     * @return the hosts, cluster health and indices, for the detail view
     */
    @ReflectiveAccess
    public ElasticsearchInfo getDetailedBody() {
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
