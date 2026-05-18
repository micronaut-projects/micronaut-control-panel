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

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.ColumnType;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import oracle.ucp.ShardConnectionStatistics;
import oracle.ucp.jdbc.JDBCConnectionPoolStatistics;
import oracle.ucp.jdbc.PoolDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataSourceServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    private DataSourceService dataSourceService;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("test_catalog");
        when(connection.getSchema()).thenReturn("test_schema");
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        dataSourceService = new DataSourceService(dataSource);
    }

    @Test
    @DisplayName("getTables with sample data returns tables with metadata")
    void testGetTablesWithSampleData() throws SQLException {
        // Given
        mockTablesResultSet();
        mockPrimaryKeysResultSet("id");
        mockForeignKeysResultSet();
        mockUniqueColumnsResultSet("email");
        mockColumnsResultSet(List.of("id", "name", "email"));

        // When
        List<Table> tables = dataSourceService.getTables();

        // Then
        assertEquals(1, tables.size());
        Table table = tables.getFirst();
        assertEquals("test_schema", table.schema());
        assertEquals("users", table.name());
        assertEquals(3, table.columns().size());
        Column idColumn = table.columns().getFirst();
        assertTrue(idColumn.isPrimaryKey());
        assertFalse(idColumn.isForeignKey());
        assertEquals(ColumnType.NUMERIC, idColumn.type());
        assertEquals("id", idColumn.name());

        Column emailColumn = table.columns().get(2);
        assertTrue(table.uniqueColumns().contains("email"));
        assertTrue(emailColumn.isForeignKey()); // Assuming from FK mock
    }

    @Test
    @DisplayName("getTables with no tables returns empty list")
    void testGetTablesWithNoTables() throws SQLException {
        // Given
        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(false);
        when(databaseMetaData.getTables(anyString(), anyString(), anyString(), any())).thenReturn(tablesRs);

        // When
        List<Table> tables = dataSourceService.getTables();

        // Then
        assertTrue(tables.isEmpty());
    }

    @Test
    @DisplayName("column display type includes size for sized types")
    void testColumnDisplayTypeIncludesSizeForSizedTypes() {
        assertEquals("TEXT(255)", new Column("name", ColumnType.TEXT, 255, "YES", false, false, false).displayType());
        assertEquals("NUMERIC(10)", new Column("id", ColumnType.NUMERIC, 10, "NO", false, true, false).displayType());
        assertEquals("BOOLEAN", new Column("active", ColumnType.BOOLEAN, 0, "YES", false, false, false).displayType());
    }

    @Test
    @DisplayName("getJdbcInfo returns database and driver metadata")
    void testGetJdbcInfo() throws SQLException {
        // Given
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(databaseMetaData.getDatabaseProductVersion()).thenReturn("17.5");
        when(databaseMetaData.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        when(databaseMetaData.getDriverVersion()).thenReturn("42.7.7");

        // When
        var jdbcInfo = dataSourceService.getJdbcInfo();

        // Then
        assertEquals("PostgreSQL", jdbcInfo.database());
        assertEquals("17.5", jdbcInfo.version());
        assertEquals("PostgreSQL JDBC Driver", jdbcInfo.driverName());
        assertEquals("42.7.7", jdbcInfo.driverVersion());
        assertEquals("test_catalog", jdbcInfo.catalog());
        assertEquals("test_schema", jdbcInfo.schema());
    }

    @Test
    @DisplayName("getPoolInfo returns empty when no optional pool inspector supports the datasource")
    void testGetPoolInfoWithoutPoolInspector() {
        assertTrue(dataSourceService.getPoolInfo().isEmpty());
    }

    @Test
    @DisplayName("getPoolInfo maps Hikari pool options and statistics")
    void testGetPoolInfoForHikari() throws SQLException {
        HikariDataSource hikariDataSource = mock(HikariDataSource.class);
        DataSource nestedDataSource = mock(DataSource.class);
        HikariPoolMXBean poolMxBean = mock(HikariPoolMXBean.class);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
        when(hikariDataSource.getPoolName()).thenReturn("main-pool");
        when(hikariDataSource.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost/test");
        when(hikariDataSource.getUsername()).thenReturn("user");
        when(hikariDataSource.getDataSource()).thenReturn(nestedDataSource);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(10);
        when(hikariDataSource.getMinimumIdle()).thenReturn(2);
        when(hikariDataSource.getConnectionTimeout()).thenReturn(30_000L);
        when(hikariDataSource.getValidationTimeout()).thenReturn(5_000L);
        when(hikariDataSource.getIdleTimeout()).thenReturn(600_000L);
        when(hikariDataSource.getMaxLifetime()).thenReturn(1_800_000L);
        when(hikariDataSource.getLeakDetectionThreshold()).thenReturn(0L);
        when(hikariDataSource.getKeepaliveTime()).thenReturn(120_000L);
        when(hikariDataSource.getLoginTimeout()).thenReturn(4);
        when(hikariDataSource.getConnectionTestQuery()).thenReturn("SELECT 1");
        when(hikariDataSource.getConnectionInitSql()).thenReturn("SET application_name = 'control-panel'");
        when(hikariDataSource.getTransactionIsolation()).thenReturn("TRANSACTION_READ_COMMITTED");
        when(hikariDataSource.isRunning()).thenReturn(true);
        when(hikariDataSource.isClosed()).thenReturn(false);
        when(hikariDataSource.getDataSourceProperties()).thenReturn(properties("ApplicationName", "socketTimeout"));
        when(hikariDataSource.getHealthCheckProperties()).thenReturn(properties("connectivityCheck"));
        when(poolMxBean.getActiveConnections()).thenReturn(3);
        when(poolMxBean.getIdleConnections()).thenReturn(4);
        when(poolMxBean.getTotalConnections()).thenReturn(7);
        when(poolMxBean.getThreadsAwaitingConnection()).thenReturn(1);
        var service = new DataSourceService(hikariDataSource, List.of(new HikariConnectionPoolInspector()));

        var poolInfo = service.getPoolInfo().orElseThrow();

        assertEquals("HikariCP", poolInfo.provider());
        assertEquals("main-pool", poolInfo.poolName());
        assertEquals(3, poolInfo.stats().active());
        assertEquals(4, poolInfo.stats().idle());
        assertEquals(7, poolInfo.stats().total());
        assertEquals(10, poolInfo.stats().max());
        assertEquals(2, poolInfo.stats().min());
        assertEquals(1, poolInfo.stats().awaiting());
        assertEquals("30%", poolInfo.stats().activeWidth());
        assertEquals("40%", poolInfo.stats().idleWidth());
        assertEquals("30%", poolInfo.stats().remainingWidth());
        assertTrue(poolInfo.optionGroups().stream().anyMatch(group -> group.title().equals("Timeouts")));
        assertTrue(poolInfo.optionGroups().stream()
            .flatMap(group -> group.options().stream())
            .anyMatch(option -> option.label().equals("Connection test query") && option.value().equals("SELECT 1")));
        assertTrue(hasPoolOption(poolInfo, "Datasource properties", "ApplicationName, socketTimeout"));
        assertTrue(hasPoolOption(poolInfo, "Login timeout", "4s"));
        assertTrue(hasPoolOption(poolInfo, "Running", "true"));
        assertTrue(hasPoolOption(poolInfo, "Health check properties", "connectivityCheck"));
    }

    @Test
    @DisplayName("getPoolInfo maps Oracle UCP pool options and statistics")
    void testGetPoolInfoForOracleUcp() throws SQLException {
        PoolDataSource poolDataSource = mock(PoolDataSource.class);
        JDBCConnectionPoolStatistics statistics = mock(JDBCConnectionPoolStatistics.class);
        when(poolDataSource.getStatistics()).thenReturn(statistics);
        when(poolDataSource.getConnectionPoolName()).thenReturn("oracle-pool");
        when(poolDataSource.getURL()).thenReturn("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        when(poolDataSource.getUser()).thenReturn("user");
        when(poolDataSource.getMaxPoolSize()).thenReturn(12);
        when(poolDataSource.getMinPoolSize()).thenReturn(3);
        when(poolDataSource.getInitialPoolSize()).thenReturn(4);
        when(poolDataSource.getMinIdle()).thenReturn(2);
        when(poolDataSource.getMaxStatements()).thenReturn(50);
        when(poolDataSource.getSQLForValidateConnection()).thenReturn("SELECT 1 FROM DUAL");
        when(poolDataSource.getDescription()).thenReturn("Oracle reporting pool");
        when(poolDataSource.getServiceName()).thenReturn("FREEPDB1");
        when(poolDataSource.getONSConfiguration()).thenReturn("nodes=host1:6200");
        when(poolDataSource.getPdbRoles()).thenReturn(properties("FREEPDB1"));
        when(poolDataSource.getConnectionProperties()).thenReturn(properties("oracle.jdbc.ReadTimeout"));
        when(poolDataSource.getConnectionWaitDuration()).thenReturn(Duration.ofSeconds(7));
        when(poolDataSource.getLoginTimeout()).thenReturn(3);
        when(poolDataSource.getUCPEventListenerProvider()).thenReturn("example.Provider");
        when(statistics.getBorrowedConnectionsCount()).thenReturn(5);
        when(statistics.getAvailableConnectionsCount()).thenReturn(4);
        when(statistics.getTotalConnectionsCount()).thenReturn(9);
        when(statistics.getPendingRequestsCount()).thenReturn(2);
        when(statistics.getPeakConnectionsCount()).thenReturn(10);
        when(statistics.getConnectionsCreatedCount()).thenReturn(11);
        when(statistics.getCumulativeConnectionBorrowedCount()).thenReturn(21L);
        when(statistics.getCumulativeConnectionUseTime()).thenReturn(4_000L);
        when(statistics.getShardConnectionStats()).thenReturn(Map.of("shard-1", mock(ShardConnectionStatistics.class)));
        var service = new DataSourceService(poolDataSource, List.of(new OracleUcpConnectionPoolInspector()));

        var poolInfo = service.getPoolInfo().orElseThrow();

        assertEquals("Oracle UCP", poolInfo.provider());
        assertEquals("oracle-pool", poolInfo.poolName());
        assertEquals(5, poolInfo.stats().active());
        assertEquals(4, poolInfo.stats().idle());
        assertEquals(9, poolInfo.stats().total());
        assertEquals(12, poolInfo.stats().max());
        assertEquals(3, poolInfo.stats().min());
        assertEquals(2, poolInfo.stats().awaiting());
        assertEquals("42%", poolInfo.stats().activeWidth());
        assertEquals("33%", poolInfo.stats().idleWidth());
        assertEquals("25%", poolInfo.stats().remainingWidth());
        assertTrue(poolInfo.optionGroups().stream().anyMatch(group -> group.title().equals("Connection")));
        assertTrue(poolInfo.optionGroups().stream()
            .flatMap(group -> group.options().stream())
            .anyMatch(option -> option.label().equals("Validation SQL") && option.value().equals("SELECT 1 FROM DUAL")));
        assertTrue(hasPoolOption(poolInfo, "Created connections", "11"));
        assertTrue(hasPoolOption(poolInfo, "Description", "Oracle reporting pool"));
        assertTrue(hasPoolOption(poolInfo, "ONS configuration", "nodes=host1:6200"));
        assertTrue(hasPoolOption(poolInfo, "PDB roles", "FREEPDB1"));
        assertTrue(hasPoolOption(poolInfo, "Login timeout", "3s"));
        assertTrue(hasPoolOption(poolInfo, "Fail fast on chunk unavailable", "Unknown"));
        assertTrue(hasPoolOption(poolInfo, "Event listener provider", "example.Provider"));
        assertTrue(hasPoolOption(poolInfo, "Borrowed connections", "21"));
        assertTrue(hasPoolOption(poolInfo, "Connection use", "4s"));
        assertTrue(hasPoolOption(poolInfo, "Shard stats", "shard-1"));
    }

    @Test
    @DisplayName("getTables with SQLException throws RuntimeException")
    void testGetTablesWithSQLException() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> dataSourceService.getTables());
    }

    @Test
    @DisplayName("executeQuery select with pagination returns paginated results")
    void testExecuteQuerySelectWithPagination() throws SQLException {
        // Given
        String sql = "SELECT * FROM users";
        mockSelectQuery(sql, List.of("id", "name"), List.of(List.of("1", "John")));

        // When
        DataSourceService.QueryResult result = dataSourceService.executeQuery(sql, 0, 1);

        // Then
        assertEquals(1, result.total());
        assertEquals(List.of("id", "name"), result.cols());
        assertEquals(2, result.columns().size());
        assertEquals("id", result.columns().getFirst().label());
        assertEquals("INTEGER", result.columns().getFirst().typeName());
        assertEquals(1, result.rows().size());
        assertEquals(List.of("1", "John"), result.rows().getFirst());
    }

    @Test
    @DisplayName("executeQuery update query returns affected rows")
    void testExecuteQueryUpdate() throws SQLException {
        // Given
        String sql = "UPDATE users SET name = 'Jane' WHERE id = 1";
        try (PreparedStatement ps = mock(PreparedStatement.class)) {
            when(ps.executeUpdate()).thenReturn(1);
            when(connection.prepareStatement(sql)).thenReturn(ps);

            // When
            DataSourceService.QueryResult result = dataSourceService.executeQuery(sql, 0, 10);

            // Then
            assertEquals(1, result.total());
            assertEquals(List.of("Affected Rows"), result.cols());
            assertEquals("INTEGER", result.columns().getFirst().typeName());
            assertEquals(List.of("1 rows affected"), result.rows().getFirst());
        }
    }

    @Test
    @DisplayName("executeQuery invalid multiple statements throws exception")
    void testExecuteQueryInvalidMultipleStatements() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> dataSourceService.executeQuery("SELECT 1; SELECT 2", 0, 10));
    }

    @Test
    @DisplayName("executeQuery with comments strips comments")
    void testExecuteQueryWithComments() throws SQLException {
        // Given
        String sqlWithComments = "SELECT * FROM users -- comment\n/* multi */";
        String cleanSql = "SELECT * FROM users";
        mockSelectQuery(cleanSql, List.of("id"), List.of(List.of("1")));

        // When
        DataSourceService.QueryResult result = dataSourceService.executeQuery(sqlWithComments, 0, 1);

        // Then
        // Verify main query was executed without comments
        verify(connection).prepareStatement(cleanSql);
        assertEquals(1, result.total());
        assertEquals(1, result.rows().size());
        assertEquals(List.of("1"), result.rows().getFirst());
    }

    @Test
    @DisplayName("executeQuery negative pagination handles defaults")
    void testExecuteQueryNegativePagination() throws SQLException {
        // Given
        String sql = "SELECT * FROM users";
        mockSelectQuery(sql, List.of("id"), List.of(List.of("dummy"))); // mock returns a single row (total=1)

        // When
        DataSourceService.QueryResult result = dataSourceService.executeQuery(sql, -5, -10);

        // Then
        assertEquals(1, result.total()); // count still executed even with negative pagination values
        assertEquals(1, result.rows().size()); // negative pagination is normalized to defaults, so the row is returned
    }

    @Test
    @DisplayName("generateMermaidER with tables generates valid diagram")
    void testGenerateMermaidERWithTables() {
        // Given
        Table table = new Table("test_schema", "users",
                List.of(new Column("id", ColumnType.NUMERIC, 10, "NO", false, true, false)),
                Set.of(), List.of());
        List<Table> tables = List.of(table);

        // When
        String mermaid = dataSourceService.generateMermaidER(tables);

        // Then
        assertNotNull(mermaid);
        assertTrue(mermaid.contains("erDiagram"));
        assertTrue(mermaid.contains("test_schema_users"));
        assertTrue(mermaid.contains("numeric(10) id PK \"NOT NULL\""));
    }

    @Test
    @DisplayName("generateMermaidER empty tables returns empty diagram")
    void testGenerateMermaidEREmptyTables() {
        // When
        String mermaid = dataSourceService.generateMermaidER(List.of());

        // Then
        assertNotNull(mermaid);
        assertTrue(mermaid.contains("erDiagram"));
        assertTrue(mermaid.contains("EMPTY"));
        assertTrue(mermaid.contains("No tables detected"));
    }

    private void mockTablesResultSet() throws SQLException {
        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true).thenReturn(false);
        when(tablesRs.getString("TABLE_SCHEM")).thenReturn("test_schema");
        when(tablesRs.getString("TABLE_NAME")).thenReturn("users");
        when(databaseMetaData.getTables(eq("test_catalog"), eq("test_schema"), eq("%"), any())).thenReturn(tablesRs);
    }

    private void mockPrimaryKeysResultSet(String... pks) throws SQLException {
        ResultSet pkRs = mock(ResultSet.class);
        when(pkRs.next()).thenReturn(true, false);
        when(pkRs.getString("COLUMN_NAME")).thenReturn(pks[0]);
        when(databaseMetaData.getPrimaryKeys("test_catalog", "test_schema", "users")).thenReturn(pkRs);
    }

    private void mockForeignKeysResultSet() throws SQLException {
        ResultSet fkRs = mock(ResultSet.class);
        when(fkRs.next()).thenReturn(true).thenReturn(false);
        when(fkRs.getString("FK_NAME")).thenReturn("fk_users");
        when(fkRs.getString("FKCOLUMN_NAME")).thenReturn("email");
        when(fkRs.getString("PKTABLE_SCHEM")).thenReturn("test_schema");
        when(fkRs.getString("PKTABLE_NAME")).thenReturn("profiles");
        when(fkRs.getString("PKCOLUMN_NAME")).thenReturn("email");
        when(databaseMetaData.getImportedKeys("test_catalog","test_schema", "users")).thenReturn(fkRs);
    }

    private void mockUniqueColumnsResultSet(String... uniques) throws SQLException {
        ResultSet idxRs = mock(ResultSet.class);
        when(idxRs.next()).thenReturn(true).thenReturn(false);
        when(idxRs.getBoolean("NON_UNIQUE")).thenReturn(false);
        when(idxRs.getString("COLUMN_NAME")).thenReturn(uniques[0]);
        when(databaseMetaData.getIndexInfo("test_catalog", "test_schema", "users", true, false)).thenReturn(idxRs);
    }

    private void mockColumnsResultSet(List<String> columnNames) throws SQLException {
        ResultSet colsRs = mock(ResultSet.class);
        AtomicInteger colIndex = new AtomicInteger(0);
        doAnswer(invocation -> {
            int current = colIndex.get();
            if (current < columnNames.size()) {
                colIndex.incrementAndGet();
                return true;
            }
            return false;
        }).when(colsRs).next();

        doAnswer(invocation -> {
            int current = colIndex.get() - 1;
            if (current >= 0 && current < columnNames.size()) {
                return columnNames.get(current);
            }
            return null;
        }).when(colsRs).getString("COLUMN_NAME");

        when(colsRs.getString("TYPE_NAME")).thenReturn("INT");
        when(colsRs.getInt("COLUMN_SIZE")).thenReturn(10);
        when(colsRs.getInt("DATA_TYPE")).thenReturn(Types.INTEGER);

        doAnswer(invocation -> {
            int current = colIndex.get() - 1;
            if (current >= 0 && current < columnNames.size()) {
                return current == 0 ? "NO" : "YES";
            }
            return "YES";
        }).when(colsRs).getString("IS_NULLABLE");

        when(databaseMetaData.getColumns("test_catalog", "test_schema", "users", "%")).thenReturn(colsRs);
    }

    private void mockSelectQuery(String sql, List<String> cols, List<List<String>> rowsData) throws SQLException {
        // Mock count query
        PreparedStatement countPs = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        when(countRs.next()).thenReturn(true).thenReturn(false);
        when(countRs.getInt(1)).thenReturn(rowsData.size());
        when(countPs.executeQuery()).thenReturn(countRs);
        when(connection.prepareStatement(argThat(s -> s != null && s.toLowerCase().contains("count")))).thenReturn(countPs);

        // Mock main query
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(md);
        when(md.getColumnCount()).thenReturn(cols.size());
        for (int j = 0; j < cols.size(); j++) {
            when(md.getColumnLabel(j + 1)).thenReturn(cols.get(j));
            when(md.getColumnTypeName(j + 1)).thenReturn(j == 0 ? "INTEGER" : "VARCHAR");
            when(md.getColumnType(j + 1)).thenReturn(j == 0 ? Types.INTEGER : Types.VARCHAR);
            when(md.getColumnClassName(j + 1)).thenReturn(String.class.getName());
        }

        AtomicInteger rowIndex = new AtomicInteger(0);
        doAnswer(invocation -> {
            if (rowIndex.get() < rowsData.size()) {
                rowIndex.incrementAndGet();
                return true;
            }
            return false;
        }).when(rs).next();

        doAnswer(invocation -> {
            int col = invocation.getArgument(0);
            int currentRow = rowIndex.get() - 1;
            if (currentRow >= 0 && currentRow < rowsData.size()) {
                List<String> row = rowsData.get(currentRow);
                if (col - 1 < row.size()) {
                    return row.get(col - 1);
                }
            }
            return null;
        }).when(rs).getString(anyInt());

        when(ps.executeQuery()).thenReturn(rs);
        when(connection.prepareStatement(sql)).thenReturn(ps);
    }

    private static Properties properties(String... names) {
        var properties = new Properties();
        for (String name : names) {
            properties.setProperty(name, "configured");
        }
        return properties;
    }

    private static boolean hasPoolOption(PoolInfo poolInfo, String label, String value) {
        return poolInfo.optionGroups().stream()
            .flatMap(group -> group.options().stream())
            .anyMatch(option -> option.label().equals(label) && option.value().equals(value));
    }
}
