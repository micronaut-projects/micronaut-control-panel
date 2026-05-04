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

import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.ColumnType;
import io.micronaut.controlpanel.panels.datasource.model.Table;
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
import java.util.List;
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
}
