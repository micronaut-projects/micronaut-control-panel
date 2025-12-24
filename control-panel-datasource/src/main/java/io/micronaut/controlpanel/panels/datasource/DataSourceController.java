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

import io.micronaut.context.BeanLocator;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Get;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * REST controller to execute SQL queries against a specific DataSource for the Control Panel.
 * Designed to work with jQuery DataTables in server-side mode.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Controller("/datasource-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class DataSourceController {

    private static final Argument<DataSourceService> SERVICE_ARGUMENT = Argument.of(DataSourceService.class);
    private static final Argument<DataSourceControlPanel> PANEL_ARGUMENT = Argument.of(DataSourceControlPanel.class);

    private final Map<String, DataSourceService> services;
    private final Map<String, DataSourceControlPanel> panels;

    private final JsonMapper jsonMapper;

    public DataSourceController(BeanLocator locator, JsonMapper jsonMapper) {
        // Map keyed by bean name (datasource name)
        this.services = locator.mapOfType(SERVICE_ARGUMENT);
        this.panels = locator.mapOfType(PANEL_ARGUMENT);
        this.jsonMapper = jsonMapper;
    }

    /**
     * <p>Build CodeMirror SQLNamespace with normalized lowercase keys for matching.</p>
     *
     * <pre>
     * { "schema": { "table": { self: {label:"EMP", type:"table"}, children: [{label:"EMPNO", type:"column"}, ...] } } }
     * </pre>
     */
    @Get(value = "/{dataSource}/schema.js", produces = "application/javascript")
    public HttpResponse<String> schemaJs(String dataSource) {
        var panel = panels.get(dataSource);
        if (panel == null) {
            return HttpResponse.notFound();
        }

        var tables = panel.getBody().tables();
        var schema = new LinkedHashMap<String, Object>();
        var counts = new LinkedHashMap<String, Integer>();

        computeSchema(tables, counts, schema);

        // Choose defaultSchema (normalized, lowercased)
        String defaultSchema = null;
        int max = -1;
        for (var e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                defaultSchema = e.getKey();
            }
        }

        try {
            var schemaJson = jsonMapper.writeValueAsString(schema);
            var defaultSchemaJson = jsonMapper.writeValueAsString(defaultSchema);
            var js = "window.codemirror=window.codemirror||{};" +
                     "window.codemirror.schema=" + schemaJson + ';' +
                     "window.codemirror.defaultSchema=" + defaultSchemaJson + ';';
            return HttpResponse.ok(js)
                .contentType(MediaType.of("application/javascript"))
                .header("Cache-Control", "no-store");
        } catch (Exception e) {
            return HttpResponse.serverError();
        }
    }

    /**
     * Execute a SQL query against the specified datasource.
     *
     * @param dataSource The name of the datasource (path parameter)
     * @param body The query request containing SQL and pagination parameters
     * @return The query results as a {@link HttpResponse} containing {@link QueryResponse}, or an error response if the query fails
     */
    @Post(value = "/{dataSource}/query", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> query(String dataSource, @Body QueryRequest body) {
        var service = services.get(dataSource);
        if (service == null) {
            return HttpResponse.notFound();
        }

        if (body.sql == null || body.sql.isBlank()) {
            return HttpResponse.badRequest(QueryResponse.of(body.draw, "SQL must not be empty"));
        }

        try {
            var result = service.executeQuery(body.sql, body.start == null ? 0 : body.start, body.length == null ? 10 : body.length);

            var resp = new QueryResponse(
                body.draw == null ? 1 : body.draw,
                result.total(),
                result.total(),
                result.rows(),
                result.cols(),
                null
            );
            return HttpResponse.ok(resp);
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(QueryResponse.of(body.draw, e.getMessage()));
        } catch (Exception e) {
            return HttpResponse.serverError(QueryResponse.of(body.draw, e.getMessage()));
        }
    }

    private static void computeSchema(final List<Table> tables, final LinkedHashMap<String, Integer> counts, final LinkedHashMap<String, Object> schema) {
        for (var t : tables) {
            var schemaLabel = t.schema() == null ? "" : t.schema();
            var schemaKey = schemaLabel.toLowerCase(Locale.ROOT);
            counts.put(schemaKey, counts.getOrDefault(schemaKey, 0) + 1);

            @SuppressWarnings("unchecked")
            var tablesInSchema = (LinkedHashMap<String, Object>) schema.get(schemaKey);
            if (tablesInSchema == null) {
                tablesInSchema = new LinkedHashMap<>();
                schema.put(schemaKey, tablesInSchema);
            }
            // Expose also original-cased schema key for case-sensitive matching
            if (!schemaKey.equals(schemaLabel) && !schemaLabel.isEmpty()) {
                schema.put(schemaLabel, tablesInSchema);
            }

            var tableLabel = t.name();
            var tableKey = tableLabel.toLowerCase(Locale.ROOT);

            // children: columns (emit as plain strings to match SQLNamespace array shape)
            var tableNode = getTableNode(t, tableLabel);

            tablesInSchema.put(tableKey, tableNode);
            // Expose also original-cased table key for case-sensitive matching
            if (!tableKey.equals(tableLabel) && !tableLabel.isEmpty()) {
                tablesInSchema.put(tableLabel, tableNode);
            }
        }
    }

    private static LinkedHashMap<String, Object> getTableNode(final Table t, final String tableLabel) {
        var cols = new ArrayList<String>();
        for (var c : t.columns()) {
            cols.add(c.name());
        }

        // table node with self/children to provide display label and icon/type
        var tableNode = new LinkedHashMap<String, Object>();
        var tableSelf = new LinkedHashMap<String, Object>();
        tableSelf.put("label", tableLabel);
        tableSelf.put("type", "table");
        tableNode.put("self", tableSelf);
        tableNode.put("children", cols);
        return tableNode;
    }

    /**
     * Request DTO for query execution.
     *
     * @param sql    SQL to execute (SELECT/WITH only)
     * @param start  Pagination start offset
     * @param length Page size
     * @param draw   DataTables draw counter
     */
    @Introspected
    public record QueryRequest(String sql, Integer start, Integer length, Integer draw) { }

    /**
     * Response DTO matching DataTables server-side structure with extra cols.
     *
     * @param draw            DataTables draw counter (echoed)
     * @param recordsTotal    Total number of rows
     * @param recordsFiltered Total number of rows after filtering (same as total)
     * @param data            Page rows (arrays of strings)
     * @param cols            Column labels used to render headers
     * @param error           Optional error message
     */
    @Serdeable
    public record QueryResponse(Integer draw,
                                       int recordsTotal,
                                       int recordsFiltered,
                                       List<List<String>> data,
                                       List<String> cols,
                                       String error) {
        static QueryResponse of(Integer draw, String message) {
            return new QueryResponse(
                draw == null ? 1 : draw,
                0,
                0,
                List.of(),
                List.of(),
                message == null ? "Error" : message
            );
        }
    }
}
