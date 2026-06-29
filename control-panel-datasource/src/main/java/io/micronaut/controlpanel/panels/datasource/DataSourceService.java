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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.ColumnType;
import io.micronaut.controlpanel.panels.datasource.model.ForeignKey;
import io.micronaut.controlpanel.panels.datasource.model.JdbcInfo;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for a specific {@link DataSource}, providing metadata about tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceService.class);
    private static final String COLUMN_NAME = "COLUMN_NAME";
    private static final String COLUMN_SIZE = "COLUMN_SIZE";
    private static final String IS_NULLABLE = "IS_NULLABLE";
    private static final String DATA_TYPE = "DATA_TYPE";
    private static final String NON_UNIQUE = "NON_UNIQUE";
    private static final Pattern MULTI_LINE_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern SINGLE_LINE_COMMENT = Pattern.compile("--.*", Pattern.MULTILINE);

    private final DataSource dataSource;
    private final List<ConnectionPoolInspector> connectionPoolInspectors;

    @Inject
    public DataSourceService(@Parameter DataSource dataSource,
                             @Nullable DataSourceUnwrapper unwrapper,
                             List<ConnectionPoolInspector> connectionPoolInspectors) {
        this(unwrapper == null ? dataSource : unwrapper.unwrap(dataSource), connectionPoolInspectors);
    }

    public DataSourceService(@Parameter DataSource dataSource, List<ConnectionPoolInspector> connectionPoolInspectors) {
        this.dataSource = dataSource;
        this.connectionPoolInspectors = connectionPoolInspectors;
    }

    public DataSourceService(@Parameter DataSource dataSource) {
        this(dataSource, List.of());
    }

    /**
     * Retrieves JDBC product and driver metadata for the datasource.
     *
     * @return JDBC metadata, or an empty metadata object if it cannot be read
     */
    public JdbcInfo getJdbcInfo() {
        try (var connection = dataSource.getConnection()) {
            var dbMetaData = connection.getMetaData();
            return new JdbcInfo(
                valueOrEmpty(dbMetaData.getDatabaseProductName()),
                valueOrEmpty(dbMetaData.getDatabaseProductVersion()),
                valueOrEmpty(dbMetaData.getDriverName()),
                valueOrEmpty(dbMetaData.getDriverVersion()),
                valueOrEmpty(connection.getCatalog()),
                valueOrEmpty(connection.getSchema())
            );
        } catch (SQLException e) {
            LOG.warn("Exception while getting JDBC metadata: {}", e.getMessage());
            return JdbcInfo.EMPTY;
        }
    }

    /**
     * Retrieves connection pool metadata when the datasource uses a supported pool provider.
     *
     * @return pool metadata for supported datasources, otherwise empty
     */
    public Optional<PoolInfo> getPoolInfo() {
        for (ConnectionPoolInspector inspector : connectionPoolInspectors) {
            try {
                var poolInfo = inspector.inspect(dataSource);
                if (poolInfo.isPresent()) {
                    return poolInfo;
                }
            } catch (RuntimeException e) {
                LOG.warn("Exception while getting connection pool metadata: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves a list of tables from the database, including schema, columns, primary keys, and foreign keys.
     *
     * @return List of {@link Table} metadata objects
     */
    public final List<Table> getTables() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Fetching database tables and metadata");
        }
        List<Table> tables = new ArrayList<>();
        try (var connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String defaultSchema = connection.getSchema();
            DatabaseMetaData dbMetaData = connection.getMetaData();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Connected. catalog='{}', schema='{}', driver='{} {}'", catalog, defaultSchema, dbMetaData.getDriverName(), dbMetaData.getDriverVersion());
            }
            try (ResultSet tablesRs = dbMetaData.getTables(catalog, defaultSchema, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    String schema = tablesRs.getString("TABLE_SCHEM");
                    if (schema == null) {
                        schema = defaultSchema;
                    }
                    String tableName = tablesRs.getString("TABLE_NAME");
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Discovered table '{}.{}'", schema, tableName);
                    }

                    // Primary keys
                    List<String> primaryKeysList = findPrimaryKeys(dbMetaData, catalog, schema, tableName);

                    // Foreign keys (full info)
                    Set<String> foreignKeyColumns = new HashSet<>();
                    List<ForeignKey> foreignKeys = findForeignKeys(dbMetaData, catalog, schema, tableName, defaultSchema, foreignKeyColumns);

                    // Unique columns (unique indexes/constraints)
                    Set<String> uniqueCols = findUniqueColumns(dbMetaData, catalog, schema, tableName);

                    // Columns
                    List<Column> columnsList = findAllColumns(dbMetaData, catalog, schema, tableName, primaryKeysList, foreignKeyColumns);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Table '{}.{}' -> columns: {}, PKs: {}, FKs: {}, unique: {}", schema, tableName, columnsList.size(), primaryKeysList.size(), foreignKeys.size(), uniqueCols.size());
                    }

                    tables.add(new Table(schema, tableName, columnsList, uniqueCols, foreignKeys));
                }
            }
        } catch (SQLException e) {
            LOG.error("SQL exception: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} tables in metadata", tables.size());
        }
        return tables;
    }

    private @NonNull List<Column> findAllColumns(final DatabaseMetaData dbMetaData,
                                                 final String catalog, final String schema,
                                                 final String tableName, final List<String> primaryKeysList,
                                                 final Set<String> foreignKeyColumns) {
        List<Column> columnsList = new ArrayList<>();
        try (ResultSet colsRs = dbMetaData.getColumns(catalog, schema, tableName, "%")) {
            while (colsRs.next()) {
                String columnName = colsRs.getString(COLUMN_NAME);
                int columnSize = colsRs.getInt(COLUMN_SIZE);
                String nullable = colsRs.getString(IS_NULLABLE);
                int dataTypeInt = colsRs.getInt(DATA_TYPE);
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} columns for table '{}.{}'", columnsList.size(), schema, tableName);
        }
        return columnsList;
    }

    private static @NonNull Set<String> findUniqueColumns(final DatabaseMetaData dbMetaData,
                                                          final String catalog, final String schema,
                                                          final String tableName) {
        Set<String> uniqueCols = new LinkedHashSet<>();
        try (ResultSet idx = dbMetaData.getIndexInfo(catalog, schema, tableName, true, false)) {
            while (idx.next()) {
                boolean nonUnique = idx.getBoolean(NON_UNIQUE);
                String col = idx.getString(COLUMN_NAME);
                // Some drivers return null rows for table-level index metadata
                if (!nonUnique && col != null) {
                    uniqueCols.add(col);
                }
            }
        } catch (SQLException e) {
            LOG.warn("Exception while getting the unique columns of the table {}: {}", tableName, e.getMessage());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} unique columns for table '{}.{}'", uniqueCols.size(), schema, tableName);
        }
        return uniqueCols;
    }

    private static @NonNull List<ForeignKey> findForeignKeys(final DatabaseMetaData dbMetaData,
                                                             final String catalog, final String schema,
                                                             final String tableName,
                                                             final String defaultSchema,
                                                             final Set<String> foreignKeyColumns) {
        List<ForeignKey> foreignKeys = new ArrayList<>();
        try (ResultSet fkRs = dbMetaData.getImportedKeys(catalog, schema, tableName)) {
            while (fkRs.next()) {
                String fkName = fkRs.getString("FK_NAME");
                String fkColumn = fkRs.getString("FKCOLUMN_NAME");
                String pkSchema = Optional.ofNullable(fkRs.getString("PKTABLE_SCHEM")).orElse(defaultSchema);
                String pkTable = fkRs.getString("PKTABLE_NAME");
                String pkColumn = fkRs.getString("PKCOLUMN_NAME");
                foreignKeyColumns.add(fkColumn);
                foreignKeys.add(new ForeignKey(fkName, fkColumn, pkSchema, pkTable, pkColumn));
            }
        } catch (SQLException e) {
            LOG.warn("Exception while getting the foreign keys of the table {}: {}", tableName, e.getMessage());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} foreign keys for table '{}.{}'", foreignKeys.size(), schema, tableName);
        }
        return foreignKeys;
    }

    private static @NonNull List<String> findPrimaryKeys(final DatabaseMetaData dbMetaData,
                                                         final String catalog, final String schema,
                                                         final String tableName) {
        List<String> primaryKeysList = new ArrayList<>();
        try (ResultSet pkRs = dbMetaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (pkRs.next()) {
                primaryKeysList.add(pkRs.getString(COLUMN_NAME));
            }
        } catch (SQLException e) {
            LOG.warn("Exception while getting the primary keys of the table {}: {}", tableName, e.getMessage());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} primary key columns for table '{}.{}'", primaryKeysList.size(), schema, tableName);
        }
        return primaryKeysList;
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

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Generate a Mermaid ER diagram for the current datasource based on the provided tables metadata.
     * It includes entities with attributes (type, size, NOT NULL) and relationships based on foreign keys.
     *
     * @param tables Tables discovered by {@link #getTables()}
     * @return Mermaid ER diagram code
     */
    public String generateMermaidER(List<Table> tables) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Generating Mermaid ER for {} tables", tables.size());
        }
        return MermaidUtils.generateMermaidER(tables);
    }

    /**
     * Sanitize, execute and paginate a SQL query.
     *
     * @param sql    The SQL to execute
     * @param start  Offset of the first row
     * @param length Maximum number of rows to return
     * @return QueryResult with column labels, rows and total row count
     */
    public QueryResult executeQuery(String sql, int start, int length) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("executeQuery called: start={}, length={}, sql='{}'", start, length, sql);
        }
        String strippedSql = stripComments(sql);
        String safeSql = sanitizeQuery(strippedSql);
        if (start < 0) {
            start = 0;
        }
        if (length < 0) {
            length = 0;
        }

        String lowered = safeSql.trim().toLowerCase();
        boolean isSelect = lowered.startsWith("select ") || lowered.startsWith("with ");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sanitized SQL='{}' (select={})", safeSql, isSelect);
        }

        try (var connection = dataSource.getConnection()) {
            int total = 0;
            List<String> cols = new ArrayList<>();
            List<QueryColumn> columns = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            if (isSelect) {
                // total count for SELECT
                String countSql = "SELECT COUNT(*) FROM (" + safeSql + ") t";
                try (var ps = connection.prepareStatement(countSql);
                     var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }

                // execute query with pagination
                try (var ps = connection.prepareStatement(safeSql);
                     var rs = ps.executeQuery()) {
                    var md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    for (int i = 1; i <= colCount; i++) {
                        String label = md.getColumnLabel(i);
                        String columnLabel = label != null ? label : ("col" + i);
                        cols.add(columnLabel);
                        columns.add(new QueryColumn(
                            columnLabel,
                            valueOrEmpty(md.getColumnTypeName(i)),
                            md.getColumnType(i),
                            valueOrEmpty(md.getColumnClassName(i))
                        ));
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
            } else {
                // for non-SELECT (INSERT, UPDATE, DELETE, etc.), execute and get update count
                try (var ps = connection.prepareStatement(safeSql)) {
                    total = ps.executeUpdate();
                }
                cols.add("Affected Rows");
                columns.add(new QueryColumn("Affected Rows", "INTEGER", Types.INTEGER, Integer.class.getName()));
                rows.add(List.of(total + " rows affected"));
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Query executed: columns={}, rowsReturned={}, total={}", cols.size(), rows.size(), total);
            }
            return new QueryResult(cols, rows, total, columns);
        } catch (SQLException e) {
            LOG.error("Error while executing SQL query: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String sanitizeQuery(@Nullable String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // Disallow stacked statements
        if (trimmed.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Multiple statements are not allowed");
        }
        return trimmed;
    }

    private @Nullable String stripComments(@Nullable String sql) {
        if (sql == null) {
            return null;
        }
        // Remove multi-line comments /* ... */
        sql = MULTI_LINE_COMMENT.matcher(sql).replaceAll("");
        // Remove single-line comments -- ...
        sql = SINGLE_LINE_COMMENT.matcher(sql).replaceAll("");
        return sql;
    }

    /**
     * Result of executing a SQL query.
     *
     * @param cols    The column labels in display order
     * @param rows    The page of rows; each row is a list of column values
     * @param total   The total number of rows for the full result set
     * @param columns JDBC metadata for each column in display order
     */
    public record QueryResult(List<String> cols, List<List<String>> rows, int total, List<QueryColumn> columns) {
        public QueryResult(List<String> cols, List<List<String>> rows, int total) {
            this(cols, rows, total, defaultColumns(cols));
        }

        private static List<QueryColumn> defaultColumns(List<String> cols) {
            return cols.stream()
                .map(col -> new QueryColumn(col, "", Types.OTHER, ""))
                .toList();
        }
    }

    /**
     * Metadata for a query result column.
     *
     * @param label     Result-set column label
     * @param typeName  JDBC type name
     * @param jdbcType  JDBC type code from {@link Types}
     * @param className Java class name reported by the driver
     */
    @Serdeable
    public record QueryColumn(String label, String typeName, int jdbcType, String className) {
    }

}
