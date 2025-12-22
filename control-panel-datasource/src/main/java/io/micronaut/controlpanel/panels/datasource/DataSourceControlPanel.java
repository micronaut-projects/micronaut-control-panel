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
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@EachBean(DataSource.class)
public class DataSourceControlPanel extends AbstractEachBeanControlPanel<DataSourceControlPanel.Body> {

    public static final String NAME = "datasource";
    public static final String ICON_CLASS = "fa-database";

    private final DataSourceInfo dataSourceInfo;
    private final String beanName;
    private final List<String> tables;

    public DataSourceControlPanel(@Parameter String beanName,
                                  @Parameter DataSourceExplorer dataSourceExplorer,
                                  Environment environment,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        var jdbUrl = environment.getProperty("datasources.%s.url".formatted(beanName), String.class, "");
        this.dataSourceInfo = new DataSourceInfo(beanName, jdbUrl);
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
        return new Body(dataSourceInfo, tables);
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
    public record Body(DataSourceInfo dataSourceInfo, List<String> tables) { }

    @ReflectiveAccess
    public record Column(String columnName, String columnType, int columnSize, String nullable, boolean binary) {
    }

    @ReflectiveAccess
    public record ForeignKey(String columnName, String referencedTable, String referencedColumn) {
    }

    @ReflectiveAccess
    public record Table(String tableSchema, String tableName, List<String> primaryKeys, List<Column> columns,
                                List<ForeignKey> foreignKeys) {
    }

    @ReflectiveAccess
    public record DataSourceInfo(String name, String jdbcUrl) {
    }

    @ReflectiveAccess
    public record DataSet(List<String> cols, List<Map<String, String>> data, String error, String message,
                          int totalNumberOfElements) {
    }
}
