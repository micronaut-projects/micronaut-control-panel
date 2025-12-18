/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.util.List;

@EachBean(DataSource.class)
public class DataSourceControlPanel extends AbstractEachBeanControlPanel<DataSourceControlPanel.Body> {

    public static final String NAME = "datasource";
    public static final String ICON_CLASS = "fa-database";

    private final DataSource dataSource;
    private final String beanName;
    private final List<String> tables;

    public DataSourceControlPanel(@Parameter DataSource dataSource,
                                  @Parameter String beanName,
                                  @Parameter DataSourceExplorer dataSourceExplorer,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.dataSource = dataSource;
        this.beanName = beanName;
        this.tables = dataSourceExplorer.findTables();
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
    public Body getBody() {
        return new Body(dataSource, tables);
    }

    @Override
    public String getBadge() {
        return String.valueOf(tables.size());
    }

    @Override
    public String getIcon() {
        return ICON_CLASS;
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Data Sources", ICON_CLASS);
    }

    @ReflectiveAccess
    public record Body(DataSource dataSource, List<String> tables) { }
}
