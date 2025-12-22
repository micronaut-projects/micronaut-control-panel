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

/**
 * Control panel for DataSource metadata, displaying tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceControlPanel extends AbstractEachBeanControlPanel<DataSourceControlPanel.Body> {

    public static final String NAME = "datasource";
    public static final String ICON_CLASS = "fa-database";

    private final String beanName;
    private final List<Table> tables;
    private final Body body;

    public DataSourceControlPanel(@Parameter String beanName,
                                  @Parameter DataSourceService dataSourceService,
                                  Environment environment,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.tables = dataSourceService.findTables();
        var jdbUrl = environment.getProperty("datasources.%s.url".formatted(beanName), String.class, "");
        this.body = new Body(new DataSourceInfo(beanName, jdbUrl), tables, dataSourceService.generateMermaidER(tables));
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
        return ICON_CLASS;
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Data Sources", ICON_CLASS);
    }

    /**
     * Body of the control panel containing DataSource info and tables.
     *
     * @param dataSourceInfo The DataSource information
     * @param tables         The list of tables
     * @param mermaidEr      The Mermaid ER diagram code generated from the datasource
     */
    @ReflectiveAccess
    public record Body(DataSourceInfo dataSourceInfo, List<Table> tables, String mermaidEr) {
    }

    /**
     * Database column metadata.
     *
     * @param name         The column name
     * @param type         The generic column type
     * @param size         The column size
     * @param nullable     Whether the column is nullable
     * @param binary       Whether the column is binary
     * @param isPrimaryKey Whether the column is a primary key
     * @param isForeignKey Whether the column is a foreign key
     */
    @ReflectiveAccess
    public record Column(String name, ColumnType type, int size, String nullable, boolean binary,
                         boolean isPrimaryKey, boolean isForeignKey) {
    }

    /**
     * Database table metadata.
     *
     * @param schema  The table schema
     * @param name    The table name
     * @param columns The columns
     */
    @ReflectiveAccess
    public record Table(String schema, String name, List<Column> columns) {
    }

    /**
     * DataSource information.
     *
     * @param name    The DataSource name
     * @param jdbcUrl The JDBC URL
     */
    @ReflectiveAccess
    public record DataSourceInfo(String name, String jdbcUrl) {
    }

    /**
     * DataSet for query results.
     *
     * @param cols                  The column names
     * @param data                  The data rows
     * @param error                 Any error
     * @param message               Any message
     * @param totalNumberOfElements Total elements
     */
    @ReflectiveAccess
    public record DataSet(List<String> cols, List<Map<String, String>> data, String error,
                          String message,
                          int totalNumberOfElements) {
    }

    /**
     * Generic column types.
     */
    @ReflectiveAccess
    public enum ColumnType {
        TEXT,
        NUMERIC,
        DATE,
        BLOB,
        BOOLEAN,
        GENERIC
    }
}
