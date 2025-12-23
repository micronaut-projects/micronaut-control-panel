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
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.ForeignKey;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for a specific {@link DataSource}, providing metadata about tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceService.class);

    private final DataSource dataSource;

    public DataSourceService(@Parameter DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    /**
     * Retrieves a list of tables from the database, including schema, columns, primary keys, and foreign keys.
     *
     * @return List of {@link Table} metadata objects
     */
    public final List<Table> getTables() {
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

                    // Primary keys
                    List<String> primaryKeysList = new ArrayList<>();
                    try (ResultSet pkRs = dbMetaData.getPrimaryKeys(catalog, schema, tableName)) {
                        while (pkRs.next()) {
                            primaryKeysList.add(pkRs.getString("COLUMN_NAME"));
                        }
                    } catch (SQLException e) {
                        LOG.warn("Exception while getting the primary keys of the table {}: {}", tableName, e.getMessage());
                    }

                    // Foreign keys (full info)
                    Set<String> foreignKeyColumns = new HashSet<>();
                    List<ForeignKey> foreignKeys = new ArrayList<>();
                    try (ResultSet fkRs = dbMetaData.getImportedKeys(catalog, schema, tableName)) {
                        while (fkRs.next()) {
                            String fkName = fkRs.getString("FK_NAME");
                            String fkColumn = fkRs.getString("FKCOLUMN_NAME");
                            String pkSchema = valueOrDefault(fkRs.getString("PKTABLE_SCHEM"), defaultSchema);
                            String pkTable = fkRs.getString("PKTABLE_NAME");
                            String pkColumn = fkRs.getString("PKCOLUMN_NAME");
                            foreignKeyColumns.add(fkColumn);
                            foreignKeys.add(new ForeignKey(fkName, fkColumn, pkSchema, pkTable, pkColumn));
                        }
                    } catch (SQLException e) {
                        LOG.warn("Exception while getting the foreign keys of the table {}: {}", tableName, e.getMessage());
                    }

                    // Unique columns (unique indexes/constraints)
                    Set<String> uniqueCols = new LinkedHashSet<>();
                    try (ResultSet idx = dbMetaData.getIndexInfo(catalog, schema, tableName, true, false)) {
                        while (idx.next()) {
                            boolean nonUnique = idx.getBoolean("NON_UNIQUE");
                            String col = idx.getString("COLUMN_NAME");
                            // Some drivers return null rows for table-level index metadata
                            if (!nonUnique && col != null) {
                                uniqueCols.add(col);
                            }
                        }
                    } catch (SQLException e) {
                        LOG.warn("Exception while getting the index info of the table {}: {}", tableName, e.getMessage());
                    }

                    // Columns
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
                            ColumnType columnType = mapToColumnType(dataTypeInt);
                            boolean isPrimaryKey = primaryKeysList.contains(columnName);
                            boolean isForeignKey = foreignKeyColumns.contains(columnName);
                            columnsList.add(new Column(columnName, columnType, columnSize, nullable, isBinary, isPrimaryKey, isForeignKey));
                        }
                    } catch (SQLException e) {
                        LOG.warn("Exception while getting the columns of the table {}: {}", tableName, e.getMessage());
                    }

                    tables.add(new Table(schema, tableName, columnsList, uniqueCols, foreignKeys));
                }
            }
        } catch (SQLException e) {
            LOG.error("SQL exception: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return tables;
    }

    private ColumnType mapToColumnType(int dataType) {
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

    /**
     * Generate a Mermaid ER diagram for the current datasource based on the provided tables metadata.
     * It includes entities with attributes (type, size, NOT NULL) and relationships based on foreign keys.
     *
     * @param tables Tables discovered by {@link #getTables()}
     * @return Mermaid ER diagram code
     */
    public String generateMermaidER(List<Table> tables) {
        return MermaidUtils.generateMermaidER(tables);
    }

    private static String valueOrDefault(String v, String def) {
        return v == null ? def : v;
    }

    /**
     * Sanitize, execute and paginate a SQL SELECT/WITH query.
     *
     * @param sql    The SQL to execute
     * @param start  Offset of the first row
     * @param length Maximum number of rows to return
     * @return QueryResult with column labels, rows and total row count
     */
    public QueryResult executeQuery(String sql, int start, int length) {
        String safeSql = sanitizeQuery(sql);
        if (start < 0) {
            start = 0;
        }
        if (length < 0) {
            length = 0;
        }

        try (var connection = dataSource.getConnection()) {
            // total count
            int total = 0;
            String countSql = "SELECT COUNT(*) FROM (" + safeSql + ") t";
            try (var ps = connection.prepareStatement(countSql);
                 var rs = ps.executeQuery()) {
                if (rs.next()) {
                    total = rs.getInt(1);
                }
            }

            List<String> cols = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            try (var ps = connection.prepareStatement(safeSql);
                 var rs = ps.executeQuery()) {
                var md = rs.getMetaData();
                int colCount = md.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    String label = md.getColumnLabel(i);
                    cols.add(label != null ? label : ("col" + i));
                }

                // skip to offset
                int skipped = 0;
                while (skipped < start && rs.next()) {
                    skipped++;
                }
                // collect page
                int collected = 0;
                while ((length == 0 || collected < length) && rs.next()) {
                    List<String> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        String v = rs.getString(i);
                        row.add(v);
                    }
                    rows.add(row);
                    collected++;
                }
            }

            return new QueryResult(cols, rows, total);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String sanitizeWordBoundary(String s) {
        return s.replaceAll("\\s+", " ").toLowerCase();
    }

    private String sanitizeQuery(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }
        String s = sql.trim();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1);
        }
        // Disallow stacked statements
        if (s.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Multiple statements are not allowed");
        }
        String lower = s.stripLeading().toLowerCase();
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new IllegalArgumentException("Only SELECT/WITH queries are allowed");
        }
        // Basic keyword blacklist to reduce risk
        String normalized = sanitizeWordBoundary(s);
        if (normalized.matches(".*\\b(insert|update|delete|merge|drop|alter|create|truncate)\\b.*")) {
            throw new IllegalArgumentException("Only read-only queries are allowed");
        }
        return s;
    }

    /**
     * Result of executing a SQL query.
     *
     * @param cols  The column labels in display order
     * @param rows  The page of rows; each row is a list of column values
     * @param total The total number of rows for the full result set
     */
    public record QueryResult(List<String> cols, List<List<String>> rows, int total) { }

}
