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

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import io.micronaut.controlpanel.panels.datasource.model.Body;
import io.micronaut.controlpanel.panels.datasource.model.DataSourceInfo;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceTemplatesTest {

    private final Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader("/", ".hbs"));

    @Test
    void tablesPanelRendersTableControlsAndDetailActions() throws IOException {
        var model = new Body(
            new DataSourceInfo("test", "jdbc:postgresql://localhost/test", "user", "", DatabaseType.POSTGRES),
            List.of(
                new Table(null, "default_table", List.of(), Set.of(), List.of()),
                new Table("public", "department", List.of(), Set.of(), List.of()),
                new Table("audit", "event_log", List.of(), Set.of(), List.of())
            ),
            ""
        );

        String html = render("views/datasource/detail-tables-panel", model);

        assertTrue(html.contains("id=\"tablesPageContainer\""));
        assertTrue(html.contains("id=\"tableSearch\""));
        assertTrue(html.contains("id=\"tableSchemaFilter\""));
        assertTrue(html.contains("<option value=\"__all__\">All schemas</option>"));
        assertTrue(html.contains("<option value=\"\">default schema</option>"));
        assertTrue(html.contains("<option value=\"public\">public</option>"));
        assertTrue(html.contains("<option value=\"audit\">audit</option>"));
        assertTrue(html.contains("id=\"tableDetailActions\""));
        assertTrue(html.contains("id=\"selectAllTableQuery\""));
        assertTrue(html.contains("id=\"displayTableAsJson\" data-table-display-json hidden disabled"));
        assertTrue(html.contains("id=\"displayTableRelationshipsAsJson\" data-table-display-relationships-json hidden disabled"));
    }

    @Test
    void detailTabsOmitPoolTabWithoutPoolInfo() throws IOException {
        var model = new Body(
            new DataSourceInfo("test", "jdbc:postgresql://localhost/test", "user", "", DatabaseType.POSTGRES),
            List.of(),
            ""
        );

        String html = render("views/datasource/detail-tabs", model);

        assertTrue(html.contains("aria-controls=\"datasourceTablesTab\""));
        assertTrue(html.contains("aria-controls=\"datasourceQueryTab\""));
        assertFalse(html.contains("datasourcePoolTab"));
    }

    @Test
    void poolPanelRendersStatusChartAndOptionsWhenPoolInfoExists() throws IOException {
        var model = new Body(
            new DataSourceInfo("test", "jdbc:postgresql://localhost/test", "user", "", DatabaseType.POSTGRES),
            List.of(),
            "",
            poolInfo()
        );

        String tabs = render("views/datasource/detail-tabs", model);
        String panel = render("views/datasource/detail-pool-panel", model);

        assertTrue(tabs.contains("aria-controls=\"datasourcePoolTab\""));
        assertTrue(panel.contains("id=\"poolStatusCardContainer\""));
        assertTrue(panel.contains("data-pool-status-refresh"));
        assertTrue(panel.contains("Connection status"));
        assertTrue(panel.contains("style=\"width: 30%;\""));
        assertTrue(panel.contains("style=\"width: 20%;\""));
        assertTrue(panel.contains("style=\"width: 50%;\""));
        assertTrue(panel.contains("<h3 class=\"card-title\">HikariCP</h3>"));
        assertTrue(panel.contains("<p class=\"card-description\">test-pool</p>"));
        assertTrue(panel.contains("<dt>Maximum pool size</dt>"));
        assertTrue(panel.contains("<dd>10</dd>"));
        assertTrue(panel.contains("com.zaxxer.hikari.HikariDataSource"));
    }

    @Test
    void poolStatusCardRendersRefreshableMetrics() throws IOException {
        String html = render("views/datasource/detail-pool-status-card", poolInfo());

        assertTrue(html.contains("data-pool-status-refresh"));
        assertTrue(html.contains("aria-label=\"Refresh connection pool status\""));
        assertTrue(html.contains("<span>Active</span>"));
        assertTrue(html.contains("<strong>3</strong>"));
        assertTrue(html.contains("<span>Waiting</span>"));
        assertTrue(html.contains("style=\"width: 50%;\""));
    }

    @Test
    void tablesPageRendersRowActionsOnlyWhenQueriesAreAvailable() throws IOException {
        var page = new DataSourceController.TablesPage(
            1,
            1,
            "Showing 1-2 of 2 tables",
            "Showing 1-2 of 2",
            List.of(new DataSourceController.PageSizeOption(10, true)),
            List.of(
                new DataSourceController.TableRow(
                    "public",
                    "department",
                    false,
                    2,
                    1,
                    "SELECT * FROM public.department",
                    "SELECT to_json(x) AS json FROM public.department x",
                    "SELECT to_jsonb(t) || jsonb_build_object('fk_employee_department', '[]'::jsonb) AS json FROM public.department t"
                ),
                new DataSourceController.TableRow(
                    "public",
                    "audit_log",
                    false,
                    2,
                    0,
                    "SELECT * FROM public.audit_log",
                    "SELECT to_json(x) AS json FROM public.audit_log x",
                    null
                )
            ),
            false,
            true,
            true
        );

        String html = render("views/datasource/detail-tables-page", page);

        assertTrue(html.contains("data-table-row data-table-schema=\"public\" data-table-name=\"department\""));
        assertTrue(html.contains("data-table-row data-table-schema=\"public\" data-table-name=\"audit_log\""));
        assertTrue(html.contains("data-table-select-sql=\"SELECT * FROM public.department\" data-table-select-all"));
        assertTrue(html.contains("data-table-json-sql=\"SELECT to_json(x) AS json FROM public.department x\" data-table-display-json"));
        assertTrue(html.contains("data-table-json-sql=\"SELECT to_json(x) AS json FROM public.audit_log x\" data-table-display-json"));
        assertTrue(html.contains("Display JSON with relationships"));
        assertEquals(1, countOccurrences(html, "data-table-display-relationships-json"));
        assertFalse(html.contains("data-table-name=\"audit_log\" data-table-relationship-json-sql"));
    }

    @Test
    void tableDetailRendersQueriesColumnsRelationshipsAndDiagram() throws IOException {
        var model = new DataSourceController.TableDetail(
            "public",
            "employee",
            "SELECT * FROM public.employee",
            "SELECT to_json(x) AS json FROM public.employee x",
            "SELECT to_jsonb(t) || jsonb_build_object('fk_employee_department', '[]'::jsonb) AS json FROM public.employee t",
            List.of(
                new DataSourceController.ColumnDetail("id", "NUMERIC(10)", "NO", true, false, false, false),
                new DataSourceController.ColumnDetail("department_id", "NUMERIC(10)", "YES", false, true, false, false),
                new DataSourceController.ColumnDetail("email", "TEXT(255)", "YES", false, false, true, false),
                new DataSourceController.ColumnDetail("name", "TEXT(255)", "YES", false, false, false, true)
            ),
            false,
            List.of(new DataSourceController.RelationshipDetail("fk_employee_department", "department_id", "public", "department", "id", "public.department")),
            false,
            "erDiagram\n  public_employee }o--|| public_department : \"fk_employee_department\""
        );

        String html = render("views/datasource/detail-table-detail", model);

        assertTrue(html.contains("data-table-select-sql=\"SELECT * FROM public.employee\""));
        assertTrue(html.contains("data-table-json-sql=\"SELECT to_json(x) AS json FROM public.employee x\""));
        assertTrue(html.contains("data-table-relationship-json-sql=\"SELECT to_jsonb(t) || jsonb_build_object(&#x27;fk_employee_department&#x27;, &#x27;[]&#x27;::jsonb) AS json FROM public.employee t\""));
        assertTrue(html.contains("<code class=\"cp-table-code\">id</code>"));
        assertTrue(html.contains("<span class=\"badge badge-secondary\">PK</span>"));
        assertTrue(html.contains("<span class=\"badge badge-secondary\">FK</span>"));
        assertTrue(html.contains("<span class=\"badge badge-secondary\">UK</span>"));
        assertTrue(html.contains("<span class=\"cp-data-table-muted\">-</span>"));
        assertTrue(html.contains("data-table-detail-link data-table-schema=\"public\" data-table-name=\"department\""));
        assertTrue(html.contains("public_employee }o--|| public_department"));
    }

    @Test
    void tableDetailOmitsRelationshipJsonQueryWhenNoRelationshipsExist() throws IOException {
        var model = new DataSourceController.TableDetail(
            "public",
            "audit_log",
            "SELECT * FROM public.audit_log",
            "SELECT to_json(x) AS json FROM public.audit_log x",
            null,
            List.of(new DataSourceController.ColumnDetail("id", "NUMERIC(10)", "NO", true, false, false, false)),
            false,
            List.of(),
            true,
            "erDiagram\n  public_audit_log"
        );

        String html = render("views/datasource/detail-table-detail", model);

        assertTrue(html.contains("data-table-json-sql=\"SELECT to_json(x) AS json FROM public.audit_log x\""));
        assertFalse(html.contains("data-table-relationship-json-sql"));
        assertTrue(html.contains("No foreign keys found for this table."));
    }

    private String render(String view, Object model) throws IOException {
        return handlebars.compile(view).apply(model);
    }

    private static int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static PoolInfo poolInfo() {
        return new PoolInfo(
            "HikariCP",
            "test-pool",
            "com.zaxxer.hikari.HikariDataSource",
            PoolInfo.PoolStats.of(3, 2, 5, 10, 1, 0),
            List.of(
                new PoolInfo.PoolOptionGroup(
                    "Sizing",
                    List.of(
                        new PoolInfo.PoolOption("Maximum pool size", "10"),
                        new PoolInfo.PoolOption("Minimum idle", "1")
                    )
                )
            )
        );
    }
}
