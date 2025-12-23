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
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.datasource.model.Body;
import io.micronaut.controlpanel.panels.datasource.model.DataSourceInfo;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.util.List;

/**
 * Control panel for DataSource metadata, displaying tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceControlPanel extends AbstractEachBeanControlPanel<Body> {

    public static final String NAME = "datasource";
    public static final String DEFAULT_ICON_CLASS = "fa-database";

    private final String beanName;
    private final List<Table> tables;
    private final Body body;
    private final DataSourceInfo dataSourceInfo;

    public DataSourceControlPanel(@Parameter String beanName,
                                  @Parameter DataSourceService dataSourceService,
                                  Environment environment,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.tables = dataSourceService.getTables();
        this.dataSourceInfo = createDataSourceInfo(environment, beanName);
        this.body = new Body(dataSourceInfo, tables, dataSourceService.generateMermaidER(tables));
    }

    private DataSourceInfo createDataSourceInfo(Environment env, String beanName) {
        var jdbUrl = env.getProperty("datasources.%s.url".formatted(beanName), String.class, "");
        var username = env.getProperty("datasources.%s.username".formatted(beanName), String.class, "");
        var password = env.getProperty("datasources.%s.password".formatted(beanName), String.class, "");
        var dialect = env.getProperty("datasources.%s.dialect".formatted(beanName), String.class, "");
        var dbType = env.getProperty("datasources.%s.db-type".formatted(beanName), String.class, "");

        return new DataSourceInfo(beanName, jdbUrl, username, password, DatabaseType.of(dialect, dbType));
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
        return body;
    }

    @Override
    public String getBadge() {
        return String.valueOf(tables.size());
    }

    @Override
    public String getIcon() {
        //TODO add custom icons
        return DEFAULT_ICON_CLASS;
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Data Sources", DEFAULT_ICON_CLASS);
    }
}
