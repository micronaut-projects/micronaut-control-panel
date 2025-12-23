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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

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

    private static final Argument<DataSourceService> ARGUMENT = Argument.of(DataSourceService.class);

    private final Map<String, DataSourceService> services;

    public DataSourceController(BeanLocator locator) {
        // Map keyed by bean name (datasource name)
        this.services = locator.mapOfType(ARGUMENT);
    }

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
