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
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.views.ModelAndView;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.micronaut.controlpanel.panels.datasource.DataSourceController.PANEL_ARGUMENT;
import static io.micronaut.controlpanel.panels.datasource.DataSourceController.SERVICE_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@MicronautTest(transactional = false)
@Property(name = "datasources.relationship-json.db-type", value = "postgres")
@Property(name = "datasources.relationship-json.driver-class-name", value = "org.postgresql.Driver")
class DataSourceRelationshipJsonQueryIntegrationTest {

    private static final String DATA_SOURCE = "relationship-json";
    private static final String RELATIONSHIP_NAME = "fk_employee_department";

    @Inject
    @Named(DATA_SOURCE)
    DataSource dataSource;

    @Inject
    Environment environment;

    @Inject
    JsonMapper jsonMapper;

    private DataSourceService dataSourceService;

    @BeforeEach
    void setUpDatabase() {
        dataSourceService = new DataSourceService(dataSource);
        dataSourceService.executeQuery("DROP TABLE IF EXISTS employee", 0, 0);
        dataSourceService.executeQuery("DROP TABLE IF EXISTS department", 0, 0);
        dataSourceService.executeQuery("""
            CREATE TABLE department (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL
            )
            """, 0, 0);
        dataSourceService.executeQuery("""
            CREATE TABLE employee (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                department_id INTEGER NOT NULL,
                CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department(id)
            )
            """, 0, 0);
        dataSourceService.executeQuery("INSERT INTO department(id, name) VALUES (1, 'Engineering'), (2, 'Finance')", 0, 0);
        dataSourceService.executeQuery("INSERT INTO employee(id, name, department_id) VALUES (10, 'Alice', 1), (11, 'Bob', 1), (12, 'Eve', 2)", 0, 0);
    }

    @Test
    void executesPostgresJsonQuery() throws IOException {
        var model = tableDetail("employee");

        assertEquals("SELECT to_json(x) AS json FROM public.employee x", model.jsonQuerySql());
        var result = dataSourceService.executeQuery(model.jsonQuerySql(), 0, 10);

        assertEquals(3, result.total());
        assertEquals(List.of("json"), result.cols());
        assertJsonColumn(result, "json");
        var alice = findJsonRow(result, "Alice");
        assertEquals(10, alice.get("id"));
        assertEquals(1, alice.get("department_id"));
        assertFalse(alice.containsKey(RELATIONSHIP_NAME));
    }

    @Test
    void executesPostgresRelationshipJsonQueryForOutgoingRelationship() throws IOException {
        var model = tableDetail("employee");

        assertNotNull(model.relationshipJsonSql());
        var result = dataSourceService.executeQuery(model.relationshipJsonSql(), 0, 10);

        assertEquals(3, result.total());
        assertEquals(List.of("json"), result.cols());
        assertJsonColumn(result, "jsonb");
        var alice = findJsonRow(result, "Alice");
        assertEquals(10, alice.get("id"));
        assertEquals(1, alice.get("department_id"));

        var relationship = assertInstanceOf(Map.class, alice.get(RELATIONSHIP_NAME));
        assertEquals(1, relationship.get("id"));
        assertEquals("Engineering", relationship.get("name"));
    }

    @Test
    void executesPostgresRelationshipJsonQueryForIncomingRelationship() throws IOException {
        var model = tableDetail("department");

        assertNotNull(model.relationshipJsonSql());
        var result = dataSourceService.executeQuery(model.relationshipJsonSql(), 0, 10);

        assertEquals(2, result.total());
        assertEquals(List.of("json"), result.cols());
        assertJsonColumn(result, "jsonb");
        var engineering = findJsonRow(result, "Engineering");
        assertEquals(1, engineering.get("id"));

        var relationship = assertInstanceOf(List.class, engineering.get(RELATIONSHIP_NAME));
        assertEquals(2, relationship.size());
        assertTrue(containsRelatedEmployee(relationship, "Alice", 1));
        assertTrue(containsRelatedEmployee(relationship, "Bob", 1));
    }

    @Test
    void queryResultIncludesPostgresColumnMetadata() {
        var result = dataSourceService.executeQuery("SELECT id, name FROM department ORDER BY id", 0, 10);

        assertEquals(2, result.total());
        assertEquals(List.of("id", "name"), result.cols());
        assertEquals(2, result.columns().size());
        var idColumn = result.columns().getFirst();
        assertEquals("id", idColumn.label());
        assertEquals("int4", idColumn.typeName().toLowerCase(Locale.ROOT));
        assertEquals(Types.INTEGER, idColumn.jdbcType());
        assertEquals(Integer.class.getName(), idColumn.className());

        var nameColumn = result.columns().get(1);
        assertEquals("name", nameColumn.label());
        assertEquals("text", nameColumn.typeName().toLowerCase(Locale.ROOT));
        assertEquals(Types.VARCHAR, nameColumn.jdbcType());
        assertEquals(String.class.getName(), nameColumn.className());
    }

    private DataSourceController.TableDetail tableDetail(String tableName) {
        var panel = new DataSourceControlPanel(
            DATA_SOURCE,
            dataSourceService,
            environment,
            new ControlPanelConfiguration(DataSourceControlPanel.NAME)
        );
        var beanLocator = mock(BeanLocator.class);
        doReturn(Map.of(DATA_SOURCE, dataSourceService)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        doReturn(Map.of(DATA_SOURCE, panel)).when(beanLocator).mapOfType(PANEL_ARGUMENT);
        var controller = new DataSourceController(beanLocator, jsonMapper);
        var response = controller.tableDetail(DATA_SOURCE, "public", tableName);
        assertEquals(HttpResponse.ok().getStatus(), response.getStatus());
        return tableDetail(response);
    }

    private DataSourceController.TableDetail tableDetail(HttpResponse<?> response) {
        var body = response.body();
        assertTrue(body instanceof ModelAndView<?>);
        var modelAndView = (ModelAndView<?>) body;
        assertEquals("datasource/detail-table-detail", modelAndView.getView().orElseThrow());
        return (DataSourceController.TableDetail) modelAndView.getModel().orElseThrow();
    }

    private Map<String, Object> findJsonRow(DataSourceService.QueryResult result, String name) throws IOException {
        for (List<String> row : result.rows()) {
            assertFalse(row.isEmpty());
            var json = jsonMapper.readValue(row.getFirst().getBytes(), Argument.mapOf(String.class, Object.class));
            if (name.equals(json.get("name"))) {
                return json;
            }
        }
        throw new AssertionError("No JSON row found for name: " + name);
    }

    private static void assertJsonColumn(DataSourceService.QueryResult result, String typeName) {
        assertEquals(1, result.columns().size());
        var column = result.columns().getFirst();
        assertEquals("json", column.label());
        assertEquals(typeName, column.typeName().toLowerCase(Locale.ROOT));
        assertEquals(Types.OTHER, column.jdbcType());
        assertEquals("org.postgresql.util.PGobject", column.className());
    }

    private static boolean containsRelatedEmployee(List<?> rows, String name, int departmentId) {
        for (Object row : rows) {
            var employee = assertInstanceOf(Map.class, row);
            if (name.equals(employee.get("name")) && Integer.valueOf(departmentId).equals(employee.get("department_id"))) {
                return true;
            }
        }
        return false;
    }
}
