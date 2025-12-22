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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller to execute SQL queries against a specific DataSource for the Control Panel.
 * Designed to work with jQuery DataTables in server-side mode.
 *
 * Endpoint: POST /datasource-control-panel-controller/{dataSource}/query
 *
 * Request JSON body (or form params):
 * - sql: SQL string to execute (only SELECT/WITH allowed)
 * - start: pagination start offset
 * - length: page size
 * - draw: DataTables draw counter
 *
 * Response JSON (DataTables server-side protocol + extra "cols"):
 * - draw
 * - recordsTotal
 * - recordsFiltered
 * - data: array of rows (each row is an array of strings)
 * - cols: array of column names (convenience for header creation)
 * - error: optional error message
 *
 * @author Micronaut Control Panel
 * @since 2.0.0
 */
@Controller("/datasource-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class DataSourceController {

    private static final Argument<DataSourceService> ARGUMENT = Argument.of(DataSourceService.class);

    private final Map<String, DataSourceService> services;

    public DataSourceController(BeanLocator locator) {
        // Map keyed by bean name (datasource name) - same pattern as ObjectStorageController
        this.services = locator.mapOfType(ARGUMENT);
    }

    @Post(value = "/{dataSource}/query", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<?> query(String dataSource, @Body QueryRequest body, HttpRequest<?> request) {
        var service = services.get(dataSource);
        if (service == null) {
            return HttpResponse.notFound();
        }

        // Extract parameters from JSON body or fallback to form/url parameters
        var params = request.getParameters();
        String sql = body != null ? body.sql() : params.get("sql");
        Integer start = body != null ? body.start() : params.get("start", Integer.class, 0);
        Integer length = body != null ? body.length() : params.get("length", Integer.class, 10);
        Integer draw = body != null ? body.draw() : params.get("draw", Integer.class, 1);

        if (sql == null || sql.isBlank()) {
            return okError(draw, "SQL must not be empty");
        }

        try {
            var result = service.executeQuery(sql, start == null ? 0 : start, length == null ? 10 : length);

            // Convert values to strings for safe JSON rendering
            List<List<String>> rows = new ArrayList<>(result.rows().size());
            for (var r : result.rows()) {
                List<String> out = new ArrayList<>(r.size());
                for (var v : r) {
                    out.add(stringify(v));
                }
                rows.add(out);
            }

            var resp = new QueryResponse(
                draw == null ? 1 : draw,
                result.total(),
                result.total(),
                rows,
                result.cols(),
                null
            );
            return HttpResponse.ok(resp);
        } catch (IllegalArgumentException e) {
            return okError(draw, e.getMessage());
        } catch (Exception e) {
            return okError(draw, "Query failed");
        }
    }

    private static String stringify(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof byte[]) {
            return "(BINARY)";
        }
        String s = String.valueOf(v);
        // Collapse newlines which may break table rendering
        return s.replace('\r', ' ').replace('\n', ' ');
    }

    private static HttpResponse<QueryResponse> okError(Integer draw, String message) {
        var resp = new QueryResponse(
            draw == null ? 1 : draw,
            0,
            0,
            List.of(),
            List.of(),
            message == null ? "Error" : message
        );
        return HttpResponse.ok(resp);
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
                                       String error) { }
}
