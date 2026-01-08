package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.BeanLocator;
import io.micronaut.controlpanel.panels.datasource.model.Body;
import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.ColumnType;
import io.micronaut.controlpanel.panels.datasource.model.DataSourceInfo;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.ForeignKey;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceControllerTest {

    @Mock BeanLocator beanLocator;
    @Mock JsonMapper jsonMapper;

    @Test
    void schemaJs_returnsNotFound_whenPanelNotFound() {
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        HttpResponse<String> response = controller.schemaJs("unknown");
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    void schemaJs_returnsJavaScript_whenPanelExists() throws Exception {
        var column = new Column("EMPNO", ColumnType.NUMERIC, 10, "YES", false, false, false);
        var uniqueCols = new HashSet<String>();
        var foreignKeys = new ArrayList<ForeignKey>();
        var table = new Table("schema1", "EMP", List.of(column), uniqueCols, foreignKeys);
        var dataSourceInfo = new DataSourceInfo("test", "", "", "", DatabaseType.POSTGRES);
        var body = new Body(dataSourceInfo, List.of(table), "");
        var panel = mock(DataSourceControlPanel.class);
        when(panel.getBody()).thenReturn(body);
        doReturn(Map.of("testDataSource", panel)).when(beanLocator).mapOfType(any(Argument.class));
        doReturn("{\"schema1\":{\"EMP\":{\"self\":{\"label\":\"EMP\",\"type\":\"table\"},\"children\":[\"EMPNO\"]}}").when(jsonMapper).writeValueAsString(any());
        doReturn("\"schema1\"").when(jsonMapper).writeValueAsString(eq("schema1"));

        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        HttpResponse<String> response = controller.schemaJs("testDataSource");
        assertEquals(200, response.getStatus().getCode());
        assertEquals("application/javascript", response.getHeaders().getContentType().get());
        String js = response.body();
        assertTrue(js.contains("window.codemirror.schema"));
        assertTrue(js.contains("schema1"));
    }

    @Test
    void query_returnsNotFound_whenServiceNotFound() {
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query("unknown", request);
        assertEquals(404, response.getStatus().getCode());
    }

    @Test
    void query_returnsBadRequest_whenSqlEmpty() {
        var service = mock(DataSourceService.class);
        doReturn(Map.of("testDataSource", service)).when(beanLocator).mapOfType(any(Argument.class));
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("", 0, 10, 1);
        HttpResponse<?> response = controller.query("testDataSource", request);
        assertEquals(400, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals("SQL must not be empty", body.error());
    }

    @Test
    void query_returnsOk_whenQuerySuccessful() throws Exception {
        var service = mock(DataSourceService.class);
        doReturn(Map.of("testDataSource", service)).when(beanLocator).mapOfType(any(Argument.class));
        var queryResult = new DataSourceService.QueryResult(List.of("col1"), List.of(List.of("value")), 1);
        when(service.executeQuery(eq("SELECT 1"), eq(0), eq(10))).thenReturn(queryResult);
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query("testDataSource", request);
        assertEquals(200, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals(1, body.recordsTotal());
        assertEquals(1, body.data().size());
        assertEquals(1, body.cols().size());
        verify(service).executeQuery(eq("SELECT 1"), eq(0), eq(10));
    }

    @Test
    void query_returnsBadRequest_whenIllegalArgument() {
        var service = mock(DataSourceService.class);
        doReturn(Map.of("testDataSource", service)).when(beanLocator).mapOfType(any(Argument.class));
        when(service.executeQuery(any(), anyInt(), anyInt())).thenThrow(new IllegalArgumentException("Invalid SQL"));
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("INVALID", 0, 10, 1);
        HttpResponse<?> response = controller.query("testDataSource", request);
        assertEquals(400, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals("Invalid SQL", body.error());
    }

    @Test
    void query_returnsServerError_whenException() {
        var service = mock(DataSourceService.class);
        doReturn(Map.of("testDataSource", service)).when(beanLocator).mapOfType(any(Argument.class));
        when(service.executeQuery(any(), anyInt(), anyInt())).thenThrow(new RuntimeException("Database error"));
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query("testDataSource", request);
        assertEquals(500, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals("Database error", body.error());
    }
}
