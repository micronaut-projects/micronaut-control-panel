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
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.Column;
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.ColumnType;
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.Table;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for a specific {@link DataSource}, providing metadata about tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceService {

    private final DataSource dataSource;

    public DataSourceService(@Parameter DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    /**
     * Retrieves a list of tables from the database, including schema, columns, primary keys, and foreign keys.
     *
     * @return List of {@link Table} metadata objects
     */
    public final List<Table> findTables() {
        List<Table> tables = new ArrayList<>();
        try (var connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String defaultSchema = connection.getSchema();
            DatabaseMetaData dbMetaData = connection.getMetaData();
            try (ResultSet tablesRs = dbMetaData.getTables(catalog, defaultSchema, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    String schema = tablesRs.getString("TABLE_SCHEM");
                    if (schema == null) {
                        schema = defaultSchema;
                    }
                    String tableName = tablesRs.getString("TABLE_NAME");

                    // Get primary keys
                    List<String> primaryKeysList = new ArrayList<>();
                    try (ResultSet pkRs = dbMetaData.getPrimaryKeys(catalog, schema, tableName)) {
                        while (pkRs.next()) {
                            primaryKeysList.add(pkRs.getString("COLUMN_NAME"));
                        }
                    } catch (SQLException pkEx) {
                        // Ignore
                    }

                    // Get foreign keys
                    Set<String> foreignKeyColumns = new HashSet<>();
                    try (ResultSet fkRs = dbMetaData.getImportedKeys(catalog, schema, tableName)) {
                        while (fkRs.next()) {
                            String fkcName = fkRs.getString("FKCOLUMN_NAME");
                            foreignKeyColumns.add(fkcName);
                        }
                    } catch (SQLException fkEx) {
                        // Ignore
                    }

                    // Get columns
                    List<Column> columnsList = new ArrayList<>();
                    try (ResultSet colsRs = dbMetaData.getColumns(catalog, schema, tableName, "%")) {
                        while (colsRs.next()) {
                            String columnName = colsRs.getString("COLUMN_NAME");
                            String columnTypeStr = colsRs.getString("TYPE_NAME");
                            int columnSize = colsRs.getInt("COLUMN_SIZE");
                            String nullable = colsRs.getString("IS_NULLABLE");
                            int dataTypeInt = colsRs.getInt("DATA_TYPE");
                            boolean isBinary = dataTypeInt == Types.BINARY ||
                                    dataTypeInt == Types.VARBINARY ||
                                    dataTypeInt == Types.LONGVARBINARY ||
                                    dataTypeInt == Types.BLOB;
                            ColumnType columnType = mapToColumnType(dataTypeInt, columnTypeStr);
                            boolean isPrimaryKey = primaryKeysList.contains(columnName);
                            boolean isForeignKey = foreignKeyColumns.contains(columnName);
                            columnsList.add(new Column(columnName, columnType, columnSize, nullable, isBinary, isPrimaryKey, isForeignKey));
                        }
                    } catch (SQLException colEx) {
                        // Ignore column errors
                    }

                    tables.add(new Table(schema, tableName, columnsList));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        System.out.println("tables = " + tables);
        return tables;
    }

    private ColumnType mapToColumnType(int dataType, String typeName) {
        return switch (dataType) {
            case Types.BIT, Types.BOOLEAN -> ColumnType.BOOLEAN;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> ColumnType.NUMERIC;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                 Types.CLOB, Types.NCLOB -> ColumnType.TEXT;
            case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ColumnType.DATE;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> ColumnType.BLOB;
            default -> ColumnType.GENERIC;
        };
    }
}
