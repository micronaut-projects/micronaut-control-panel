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
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static io.micronaut.controlpanel.panels.datasource.DataSourceController.PANEL_ARGUMENT;
import static io.micronaut.controlpanel.panels.datasource.DataSourceController.SERVICE_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceControllerTest {

    private static final String DATA_SOURCE = "testDataSource";
    @Mock BeanLocator beanLocator;
    @Mock JsonMapper jsonMapper;

    @Test
    void schemaJs_returnsNotFound_whenPanelNotFound() {
        // Given
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));

        // When
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
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
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        HttpResponse<String> response = controller.schemaJs(DATA_SOURCE);

        // Then
        assertEquals(200, response.getStatus().getCode());
        assertEquals("application/javascript", response.getHeaders().getContentType().get());
        String js = response.body();
        assertTrue(js.contains("window.codemirror.schema"));
        assertTrue(js.contains("schema1"));
    }

    @Test
    void query_returnsNotFound_whenServiceNotFound() {
        // Given
        doReturn(Map.of()).when(beanLocator).mapOfType(any(Argument.class));

        // When
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
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
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
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
        var queryResult = new DataSourceService.QueryResult(List.of("col1"), List.of(List.of("value")), 1);
        when(service.executeQuery("SELECT 1", 0, 10)).thenReturn(queryResult);
        mockPanel();

        // When
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query(DATA_SOURCE, request);

        // Then
        assertEquals(200, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals(1, body.recordsTotal());
        assertEquals(1, body.data().size());
        assertEquals(1, body.cols().size());
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
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
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
        DataSourceController controller = new DataSourceController(beanLocator, jsonMapper);
        var request = new DataSourceController.QueryRequest("SELECT 1", 0, 10, 1);
        HttpResponse<?> response = controller.query(DATA_SOURCE, request);

        // Then
        assertEquals(500, response.getStatus().getCode());
        var body = (DataSourceController.QueryResponse) response.body();
        assertEquals("Database error", body.error());
    }

    private void mockPanel() {
        var column = new Column("EMPNO", ColumnType.NUMERIC, 10, "YES", false, false, false);
        var uniqueCols = new HashSet<String>();
        var foreignKeys = new ArrayList<ForeignKey>();
        var table = new Table("schema1", "EMP", List.of(column), uniqueCols, foreignKeys);
        var dataSourceInfo = new DataSourceInfo("test", "", "", "", DatabaseType.POSTGRES);
        var body = new Body(dataSourceInfo, List.of(table), "");
        var panel = mock(DataSourceControlPanel.class);
        when(panel.getBody()).thenReturn(body);
        when(panel.getBeanName()).thenReturn(DATA_SOURCE);
        doReturn(Map.of(DATA_SOURCE, panel)).when(beanLocator).mapOfType(PANEL_ARGUMENT);
    }
}
