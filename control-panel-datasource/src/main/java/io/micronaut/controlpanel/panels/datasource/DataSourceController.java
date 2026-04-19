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
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.cachecontrol.CacheControl;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * REST controller to execute SQL queries against a specific DataSource for the Control Panel.
 * Designed to work with jQuery DataTables in server-side mode.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Controller(ControlPanelSecurityPaths.DATASOURCE)
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class DataSourceController {

    static final Argument<DataSourceService> SERVICE_ARGUMENT = Argument.of(DataSourceService.class);
    static final Argument<DataSourceControlPanel> PANEL_ARGUMENT = Argument.of(DataSourceControlPanel.class);

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceController.class);

    private final Map<String, DataSourceService> services;
    private final Map<String, DataSourceControlPanel> panels;
    private final Map<String, Schema> schemas;

    private final JsonMapper jsonMapper;

    public DataSourceController(BeanLocator locator, JsonMapper jsonMapper) {
        // Map keyed by bean name (datasource name)
        this.services = locator.mapOfType(SERVICE_ARGUMENT);
        this.panels = locator.mapOfType(PANEL_ARGUMENT);
        this.schemas = computeSchemas();
        this.jsonMapper = jsonMapper;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initialized DataSourceController with services={}, panels={}", services.keySet(), panels.keySet());
        }
    }

    /**
     * <p>Build CodeMirror SQLNamespace with normalized lowercase keys for matching.</p>
     *
     * <pre>
     * { "schema": { "table": { self: {label:"EMP", type:"table"}, children: [{label:"EMPNO", type:"column"}, ...] } } }
     * </pre>
     *
     * @param dataSource The name of the datasource
     * @return HttpResponse containing the generated schema.js JavaScript
     */
    @Get(value = "/{dataSource}/schema.js", produces = "application/javascript")
    public HttpResponse<String> schemaJs(String dataSource) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("schemaJs requested for dataSource='{}'", dataSource);
        }
        var cachedSchema = schemas.get(dataSource);
        if (cachedSchema == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No control panel found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        var schema = cachedSchema.schema;
        var defaultSchema = cachedSchema.defaultSchema;

        try {
            var schemaJson = jsonMapper.writeValueAsString(schema);
            var defaultSchemaJson = jsonMapper.writeValueAsString(defaultSchema);
            var js = "window.codemirror=window.codemirror||{};" +
                     "window.codemirror.schema=" + schemaJson + ';' +
                     "window.codemirror.defaultSchema=" + defaultSchemaJson + ';';
            if (LOG.isDebugEnabled()) {
                LOG.debug("schemaJs built for dataSource='{}' with defaultSchema='{}' and {} tables", dataSource, defaultSchema, ((Map<?, ?>) schema.getOrDefault(defaultSchema == null ? "" : defaultSchema, Map.of())).size());
            }
            return HttpResponse.ok(js)
                .contentType(MediaType.of("application/javascript"))
                .cacheControl(CacheControl.builder().noCache().build());
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to serialize schema for dataSource='{}': {}", dataSource, e.getMessage());
            }
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
    public HttpResponse<QueryResponse> query(String dataSource, @Body QueryRequest body) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("query requested for dataSource='{}' (start={}, length={}, draw={}) sql='{}'", dataSource, body.start, body.length, body.draw, body.sql);
        }
        var service = services.get(dataSource);
        if (service == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No service found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        if (body.sql == null || body.sql.isBlank()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Empty SQL received for dataSource='{}'", dataSource);
            }
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("query completed for dataSource='{}' -> total={}, rowsPage={}, cols={}", dataSource, result.total(), result.rows().size(), result.cols().size());
            }
            return HttpResponse.ok(resp);
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad request for dataSource='{}': {}", dataSource, e.getMessage());
            }
            return HttpResponse.badRequest(QueryResponse.of(body.draw, e.getMessage()));
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Query failed for dataSource='{}': {}", dataSource, e.getMessage());
            }
            return HttpResponse.serverError(QueryResponse.of(body.draw, e.getMessage()));
        }
    }

    private Map<String, Schema> computeSchemas() {
        Map<String, Schema> result = HashMap.newHashMap(panels.size());
        for (var panel : panels.values()) {
            var tables = panel.getBody().tables();
            var schema = new LinkedHashMap<String, Object>();
            var counts = new LinkedHashMap<String, Integer>();

            computeSchema(tables, counts, schema);

            // Choose defaultSchema (original case, most common)
            String defaultSchema = StringUtils.EMPTY_STRING;
            int max = -1;
            for (var e : counts.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    defaultSchema = e.getKey();
                }
            }
            result.put(panel.getBeanName(), new Schema(schema, defaultSchema));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void computeSchema(final List<Table> tables, final Map<String, Integer> counts, final Map<String, Object> schema) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Computing schema for {} tables", tables.size());
        }
        for (var t : tables) {
            var schemaKey = (t.schema() == null) ? "" : t.schema();
            counts.put(schemaKey, counts.getOrDefault(schemaKey, 0) + 1);

            var tablesInSchema = (LinkedHashMap<String, Object>) schema.get(schemaKey);
            if (tablesInSchema == null) {
                tablesInSchema = new LinkedHashMap<>();
                schema.put(schemaKey, tablesInSchema);
            }

            var tableLabel = t.name();

            // children: columns (emit as plain strings to match SQLNamespace array shape)
            var tableNode = getTableNode(t, tableLabel);

            tablesInSchema.put(tableLabel, tableNode);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Schema computed with {} schemas", schema.size());
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

    record Schema(Map<String, Object> schema, String defaultSchema) {
    }
}
