package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.BeanLocator;
import io.micronaut.controlpanel.ui.ControlPanelRenderer;
import io.micronaut.controlpanel.panels.datasource.model.Body;
import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.ColumnType;
import io.micronaut.controlpanel.panels.datasource.model.DataSourceInfo;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.ForeignKey;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.controlpanel.panels.datasource.DataSourceController.PANEL_ARGUMENT;
import static io.micronaut.controlpanel.panels.datasource.DataSourceController.SERVICE_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceControllerTest {

    private static final String DATA_SOURCE = "testDataSource";
    @Mock BeanLocator beanLocator;
    @Mock JsonMapper jsonMapper;
    @Mock ControlPanelRenderer renderer;
    private String renderedTemplate;
    private Object renderedModel;

    @BeforeEach
    void captureRenderedFragment() {
        lenient().doAnswer(invocation -> {
            renderedTemplate = invocation.getArgument(0);
            renderedModel = invocation.getArgument(1);
            return "";
        }).when(renderer).render(any(String.class), any());
    }

    @Test
    void schemaJs_returnsNotFound_whenPanelNotFound() {
        // Given
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));

        // When
        DataSourceController controller = controller();
        HttpResponse<String> response = controller.schemaJs("unknown");

        // Then
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    void schemaJs_returnsJavaScript_whenPanelExists() throws Exception {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        doReturn("{\"schema1\":{\"EMP\":{\"self\":{\"label\":\"EMP\",\"type\":\"table\"},\"children\":[\"EMPNO\"]}}").when(jsonMapper).writeValueAsString(any());
        doReturn("\"schema1\"").when(jsonMapper).writeValueAsString("schema1");
        mockPanel();

        // When
        DataSourceController controller = controller();
        HttpResponse<String> response = controller.schemaJs(DATA_SOURCE);

        // Then
        assertEquals(200, response.getStatus().getCode());
        assertEquals("application/javascript", response.getHeaders().getContentType().get());
        String js = response.body();
        assertTrue(js.contains("window.codemirror.schema"));
        assertTrue(js.contains("schema1"));
    }

    @Test
    void tables_returnsNotFound_whenPanelNotFound() {
        // Given
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables("unknown", 1, 10, null, null);

        // Then
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    void tables_returnsOnlyRequestedHtmlPage_whenPanelExists() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var tables = new ArrayList<Table>();
        tables.add(table("schema1", "EMP"));
        tables.add(table("schema1", "DEPT"));
        for (int i = 1; i <= 9; i++) {
            tables.add(table("schema1", "ORDERS_" + i));
        }
        mockPanel(tables);

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables(DATA_SOURCE, 1, 10, null, null);

        // Then
        assertEquals(200, response.getStatus().getCode());
        assertEquals(MediaType.TEXT_HTML, response.getHeaders().getContentType().get());
        var model = tablesPage(response);
        assertEquals("Showing 1-10 of 11 tables", model.summary());
        assertEquals(10, model.tables().size());
        assertTrue(model.tables().stream().anyMatch(row -> row.name().equals("EMP")));
        assertTrue(model.tables().stream().anyMatch(row -> row.name().equals("DEPT")));
        assertTrue(model.tables().stream().anyMatch(row -> row.selectAllSql().equals("SELECT * FROM schema1.EMP")));
        assertTrue(model.tables().stream().anyMatch(row -> row.jsonQuerySql().equals("SELECT to_json(x) AS json FROM schema1.EMP x")));
        assertFalse(model.tables().stream().anyMatch(row -> row.name().equals("ORDERS_9")));
    }

    @Test
    void tables_includesRelationshipJsonQueryAction_whenTableHasRelationships() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var department = table("schema1", "DEPT", "DEPTNO", "DNAME");
        var employee = new Table(
            "schema1",
            "EMP",
            List.of(
                new Column("EMPNO", ColumnType.NUMERIC, 10, "NO", false, true, false),
                new Column("DEPTNO", ColumnType.NUMERIC, 10, "YES", false, false, true)
            ),
            new HashSet<>(),
            List.of(new ForeignKey("FK_EMP_DEPT", "DEPTNO", "schema1", "DEPT", "DEPTNO"))
        );
        mockPanel(List.of(department, employee));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables(DATA_SOURCE, 1, 10, null, null);

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tablesPage(response);
        var departmentRow = model.tables().stream()
            .filter(row -> row.name().equals("DEPT"))
            .findFirst()
            .orElseThrow();
        assertNotNull(departmentRow.relationshipJsonSql());
        assertTrue(departmentRow.relationshipJsonSql().contains("'FK_EMP_DEPT'"));
    }

    @Test
    void tables_omitsRelationshipJsonQueryAction_whenTableHasNoRelationships() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        mockPanel(List.of(table("schema1", "EMP")));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables(DATA_SOURCE, 1, 10, null, null);

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tablesPage(response);
        assertEquals(1, model.tables().size());
        assertNull(model.tables().getFirst().relationshipJsonSql());
    }

    @Test
    void tables_normalizesInvalidPagingValues_whenPanelExists() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var tables = new ArrayList<Table>();
        for (int i = 1; i <= 12; i++) {
            tables.add(table("schema1", "TABLE_" + i));
        }
        mockPanel(tables);

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables(DATA_SOURCE, -1, 999, null, null);

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tablesPage(response);
        assertEquals(1, model.page());
        assertEquals(2, model.pageCount());
        assertEquals(10, model.tables().size());
        assertTrue(model.previousDisabled());
        assertFalse(model.nextDisabled());
        assertTrue(model.pageSizes().stream().anyMatch(option -> option.value() == 10 && option.selected()));
    }

    @Test
    void tables_filtersRequestedHtmlPageBySchema_whenSchemaProvided() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var tables = List.of(
            table("schema1", "EMP"),
            table("schema2", "AUDIT")
        );
        mockPanel(tables);

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables(DATA_SOURCE, 1, 10, "schema2", null);

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tablesPage(response);
        assertEquals("Showing 1-1 of 1 tables", model.summary());
        assertEquals(1, model.tables().size());
        assertEquals("AUDIT", model.tables().getFirst().name());
    }

    @Test
    void tables_filtersRequestedHtmlPageBySearch_whenSearchProvided() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var tables = List.of(
            table("schema1", "EMP"),
            table("schema1", "DEPT"),
            table("schema2", "AUDIT_LOG")
        );
        mockPanel(tables);

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tables(DATA_SOURCE, 1, 10, null, "dept");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tablesPage(response);
        assertEquals("Showing 1-1 of 1 tables", model.summary());
        assertEquals(1, model.tables().size());
        assertEquals("DEPT", model.tables().getFirst().name());
    }

    @Test
    void tableDetail_returnsFocusedHtml_whenTableExists() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var department = table("schema1", "DEPT", "DEPTNO", "DNAME");
        var employee = new Table(
            "schema1",
            "EMP",
            List.of(
                new Column("EMPNO", ColumnType.NUMERIC, 10, "NO", false, true, false),
                new Column("DEPTNO", ColumnType.NUMERIC, 10, "YES", false, false, true)
            ),
            new HashSet<>(),
            List.of(new ForeignKey("FK_EMP_DEPT", "DEPTNO", "schema1", "DEPT", "DEPTNO"))
        );
        mockPanel(List.of(department, employee));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tableDetail(DATA_SOURCE, "schema1", "EMP");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tableDetail(response);
        assertEquals("EMP", model.name());
        assertEquals("schema1", model.schema());
        assertEquals("SELECT * FROM schema1.EMP", model.selectAllSql());
        assertEquals("SELECT to_json(x) AS json FROM schema1.EMP x", model.jsonQuerySql());
        assertEquals("SELECT to_jsonb(t) || jsonb_build_object('FK_EMP_DEPT', " +
            "(SELECT to_jsonb(r1) FROM schema1.DEPT r1 WHERE r1.DEPTNO = t.DEPTNO LIMIT 1)) AS json FROM schema1.EMP t",
            model.relationshipJsonSql());
        assertEquals(2, model.columns().size());
        assertEquals("EMPNO", model.columns().getFirst().name());
        assertEquals(1, model.relationships().size());
        assertEquals("FK_EMP_DEPT", model.relationships().getFirst().name());
        assertEquals("schema1", model.relationships().getFirst().pkSchema());
        assertEquals("DEPT", model.relationships().getFirst().pkTable());
        assertEquals("schema1.DEPT", model.relationships().getFirst().pkQualifiedName());
        assertEquals("DEPTNO", model.relationships().getFirst().pkColumn());
    }

    @Test
    void tableDetail_returnsColumnOutputWithKeyFlags() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var department = table("schema1", "DEPT", "DEPTNO");
        var employee = new Table(
            "schema1",
            "EMP",
            List.of(
                new Column("EMPNO", ColumnType.NUMERIC, 10, "NO", false, true, false),
                new Column("DEPTNO", ColumnType.NUMERIC, 10, "YES", false, false, true),
                new Column("EMAIL", ColumnType.TEXT, 255, "YES", false, false, false),
                new Column("ENAME", ColumnType.TEXT, 20, "YES", false, false, false)
            ),
            Set.of("EMAIL"),
            List.of(new ForeignKey("FK_EMP_DEPT", "DEPTNO", "schema1", "DEPT", "DEPTNO"))
        );
        mockPanel(List.of(department, employee));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tableDetail(DATA_SOURCE, "schema1", "EMP");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tableDetail(response);
        assertEquals(4, model.columns().size());
        var primaryKey = model.columns().getFirst();
        assertEquals("EMPNO", primaryKey.name());
        assertEquals("NUMERIC(10)", primaryKey.displayType());
        assertEquals("NO", primaryKey.nullable());
        assertTrue(primaryKey.primaryKey());
        assertFalse(primaryKey.noKeys());

        var foreignKey = model.columns().get(1);
        assertEquals("DEPTNO", foreignKey.name());
        assertTrue(foreignKey.foreignKey());
        assertFalse(foreignKey.noKeys());

        var unique = model.columns().get(2);
        assertEquals("EMAIL", unique.name());
        assertEquals("TEXT(255)", unique.displayType());
        assertTrue(unique.unique());
        assertFalse(unique.noKeys());

        var plain = model.columns().get(3);
        assertEquals("ENAME", plain.name());
        assertTrue(plain.noKeys());
    }

    @Test
    void tableDetail_omitsRelationshipJsonQuery_whenTableHasNoRelationships() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        mockPanel(List.of(table("schema1", "EMP")));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tableDetail(DATA_SOURCE, "schema1", "EMP");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tableDetail(response);
        assertNull(model.relationshipJsonSql());
        assertTrue(model.relationshipsEmpty());
    }

    @Test
    void tableDetail_returnsPostgresRelationshipJsonQuery_whenTableHasChildren() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var department = table("schema1", "DEPT", "DEPTNO", "DNAME");
        var employee = new Table(
            "schema1",
            "EMP",
            List.of(
                new Column("EMPNO", ColumnType.NUMERIC, 10, "NO", false, true, false),
                new Column("DEPTNO", ColumnType.NUMERIC, 10, "YES", false, false, true)
            ),
            new HashSet<>(),
            List.of(new ForeignKey("FK_EMP_DEPT", "DEPTNO", "schema1", "DEPT", "DEPTNO"))
        );
        mockPanel(List.of(department, employee));

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tableDetail(DATA_SOURCE, "schema1", "DEPT");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tableDetail(response);
        String expected = "SELECT to_jsonb(t) || jsonb_build_object('FK_EMP_DEPT', " +
            "COALESCE((SELECT jsonb_agg(to_jsonb(c1)) FROM schema1.EMP c1 WHERE c1.DEPTNO = t.DEPTNO), '[]'::jsonb)) AS json FROM schema1.DEPT t";
        assertEquals(expected, model.relationshipJsonSql());
    }

    @Test
    void tableDetail_returnsOracleJsonQuery_whenDatasourceIsOracle() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        mockPanel(List.of(table("schema1", "EMP")), DatabaseType.ORACLE);

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tableDetail(DATA_SOURCE, "schema1", "EMP");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tableDetail(response);
        assertEquals("SELECT JSON_OBJECT(* RETURNING JSON) AS json FROM schema1.EMP", model.jsonQuerySql());
    }

    @Test
    void tableDetail_returnsOracleRelationshipJsonQuery_whenTableHasChildren() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        var department = table("schema1", "DEPT", "DEPTNO", "DNAME");
        var employee = new Table(
            "schema1",
            "EMP",
            List.of(
                new Column("EMPNO", ColumnType.NUMERIC, 10, "NO", false, true, false),
                new Column("DEPTNO", ColumnType.NUMERIC, 10, "YES", false, false, true)
            ),
            new HashSet<>(),
            List.of(new ForeignKey("FK_EMP_DEPT", "DEPTNO", "schema1", "DEPT", "DEPTNO"))
        );
        mockPanel(List.of(department, employee), DatabaseType.ORACLE);

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.tableDetail(DATA_SOURCE, "schema1", "DEPT");

        // Then
        assertEquals(200, response.getStatus().getCode());
        var model = tableDetail(response);
        String expected = "SELECT JSON_OBJECT(t.*, 'FK_EMP_DEPT' VALUE " +
            "COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT(c1.* RETURNING JSON) RETURNING JSON) FROM schema1.EMP c1 " +
            "WHERE c1.DEPTNO = t.DEPTNO), JSON_ARRAY(RETURNING JSON)) FORMAT JSON RETURNING JSON) AS json FROM schema1.DEPT t";
        assertEquals(expected, model.relationshipJsonSql());
    }

    @Test
    void poolStatus_returnsPoolStatusFragment_whenPoolExists() {
        // Given
        var service = mock(DataSourceService.class);
        var poolInfo = poolInfo();
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        when(service.getPoolInfo()).thenReturn(Optional.of(poolInfo));
        mockPanel();

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.poolStatus(DATA_SOURCE);

        // Then
        assertEquals(200, response.getStatus().getCode());
        assertEquals(poolInfo, fragment(response, "datasource/detail-pool-status-card").model());
    }

    @Test
    void poolStatus_returnsNotFound_whenPoolAbsent() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        when(service.getPoolInfo()).thenReturn(Optional.empty());
        mockPanel();

        // When
        DataSourceController controller = controller();
        HttpResponse<?> response = controller.poolStatus(DATA_SOURCE);

        // Then
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    void query_returnsNotFound_whenServiceNotFound() {
        // Given
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));

        // When
        DataSourceController controller = controller();
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query("unknown", request);

        // Then
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    void query_returnsBadRequest_whenSqlEmpty() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        mockPanel();

        // When
        DataSourceController controller = controller();
        var request = new DataSourceController.QueryRequest("", 0, 10, 1);
        HttpResponse<?> response = controller.query(DATA_SOURCE, request);

        // Then
        assertEquals(400, response.getStatus().getCode());
        var responseBody = (DataSourceController.QueryResponse) response.body();
        assertEquals("SQL must not be empty", responseBody.error());
    }

    @Test
    void query_returnsOk_whenQuerySuccessful() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(any(Argument.class));
        var queryResult = new DataSourceService.QueryResult(
            List.of("col1"),
            List.of(List.of("value")),
            1,
            List.of(new DataSourceService.QueryColumn("col1", "JSONB", Types.OTHER, "org.postgresql.util.PGobject"))
        );
        when(service.executeQuery("SELECT 1", 0, 10)).thenReturn(queryResult);
        mockPanel();

        // When
        DataSourceController controller = controller();
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query(DATA_SOURCE, request);

        // Then
        assertEquals(200, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals(1, body.recordsTotal());
        assertEquals(1, body.data().size());
        assertEquals(1, body.cols().size());
        assertEquals(1, body.columns().size());
        assertEquals("col1", body.columns().getFirst().label());
        assertEquals("JSONB", body.columns().getFirst().typeName());
        assertEquals(Types.OTHER, body.columns().getFirst().jdbcType());
        assertEquals("org.postgresql.util.PGobject", body.columns().getFirst().className());
        verify(service).executeQuery("SELECT 1", 0, 10);
    }

    @Test
    void query_returnsBadRequest_whenIllegalArgument() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(any(Argument.class));
        when(service.executeQuery(any(), anyInt(), anyInt())).thenThrow(new IllegalArgumentException("Invalid SQL"));
        mockPanel();

        // When
        DataSourceController controller = controller();
        var request = new DataSourceController.QueryRequest("INVALID", 0, 10, 1);
        HttpResponse<?> response = controller.query(DATA_SOURCE, request);

        // Then
        assertEquals(400, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals("Invalid SQL", body.error());
    }

    @Test
    void query_returnsServerError_whenException() {
        // Given
        var service = mock(DataSourceService.class);
        doReturn(Map.of(DATA_SOURCE, service)).when(beanLocator).mapOfType(any(Argument.class));
        when(service.executeQuery(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("Database error"));
        mockPanel();

        // When
        DataSourceController controller = controller();
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query(DATA_SOURCE, request);

        // Then
        assertEquals(500, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals("Database error", body.error());
    }

    private DataSourceController controller() {
        return new DataSourceController(beanLocator, jsonMapper, renderer);
    }

    private DataSourceController.TablesPage tablesPage(HttpResponse<?> response) {
        return (DataSourceController.TablesPage) fragment(response, "datasource/detail-tables-page").model();
    }

    private DataSourceController.TableDetail tableDetail(HttpResponse<?> response) {
        return (DataSourceController.TableDetail) fragment(response, "datasource/detail-table-detail").model();
    }

    private static PoolInfo poolInfo() {
        return new PoolInfo(
            "HikariCP",
            "test-pool",
            "com.zaxxer.hikari.HikariDataSource",
            PoolInfo.PoolStats.of(2, 3, 5, 10, 1, 0),
            List.of()
        );
    }

    private Fragment fragment(HttpResponse<?> response, String view) {
        assertEquals("", response.body());
        assertEquals("controlpanelviews/" + view, renderedTemplate);
        return new Fragment(renderedModel);
    }

    private record Fragment(Object model) {
    }

    private void mockPanel() {
        mockPanel(List.of(table("schema1", "EMP")));
    }

    private void mockPanel(List<Table> tables) {
        mockPanel(tables, DatabaseType.POSTGRES);
    }

    private void mockPanel(List<Table> tables, DatabaseType databaseType) {
        var dataSourceInfo = new DataSourceInfo("test", "", "", "", databaseType);
        var body = new Body(dataSourceInfo, tables, "");
        var panel = mock(DataSourceControlPanel.class);
        when(panel.getBody()).thenReturn(body);
        when(panel.getBeanName()).thenReturn(DATA_SOURCE);
        doReturn(Map.of(DATA_SOURCE, panel)).when(beanLocator).mapOfType(PANEL_ARGUMENT);
    }

    private static Table table(String schema, String name, String... columnNames) {
        var columns = new ArrayList<Column>();
        if (columnNames.length == 0) {
            columns.add(new Column("EMPNO", ColumnType.NUMERIC, 10, "YES", false, false, false));
        } else {
            for (String columnName : columnNames) {
                columns.add(new Column(columnName, ColumnType.NUMERIC, 10, "YES", false, false, false));
            }
        }
        var uniqueCols = new HashSet<String>();
        var foreignKeys = new ArrayList<ForeignKey>();
        return new Table(schema, name, columns, uniqueCols, foreignKeys);
    }
}
