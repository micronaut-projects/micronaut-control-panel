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
import io.micronaut.controlpanel.ui.ControlPanelRenderer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.controlpanel.panels.datasource.DataSourceController.PANEL_ARGUMENT;
import static io.micronaut.controlpanel.panels.datasource.DataSourceController.SERVICE_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;

@MicronautTest(transactional = false)
@Property(name = "datasources.relationship-json-oracle.db-type", value = "oracle")
@Property(name = "datasources.relationship-json-oracle.driver-class-name", value = "oracle.jdbc.OracleDriver")
class DataSourceOracleRelationshipJsonQueryIntegrationTest {

    private static final String DATA_SOURCE = "relationship-json-oracle";
    private static final String DEPT_RELATIONSHIP_NAME = "FK_DEPTNO";
    private static final String MANAGER_RELATIONSHIP_NAME = "FK_EMPNO_one";
    private static final String REPORTS_RELATIONSHIP_NAME = "FK_EMPNO_many";

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
        dropTable("EMP");
        dropTable("DEPT");
        dataSourceService.executeQuery("""
            create table DEPT
            (
                DEPTNO NUMBER(2) not null
                    constraint PK_DEPT
                        primary key,
                DNAME  VARCHAR2(14),
                LOC    VARCHAR2(13)
            )
            """, 0, 0);
        dataSourceService.executeQuery("""
            create table EMP
            (
                EMPNO    NUMBER(4) not null
                    constraint PK_EMP
                        primary key,
                ENAME    VARCHAR2(10),
                JOB      VARCHAR2(9),
                MGR      NUMBER(4)
                    constraint FK_EMPNO
                        references EMP,
                HIREDATE DATE,
                SAL      NUMBER(7, 2),
                COMM     NUMBER(7, 2),
                DEPTNO   NUMBER(2)
                    constraint FK_DEPTNO
                        references DEPT
            )
            """, 0, 0);
        dataSourceService.executeQuery("""
            insert into DEPT (DEPTNO, DNAME, LOC)
            values  (10, 'ACCOUNTING', 'NEW YORK'),
                    (20, 'RESEARCH', 'DALLAS'),
                    (30, 'SALES', 'CHICAGO'),
                    (40, 'OPERATIONS', 'BOSTON')
            """, 0, 0);
        dataSourceService.executeQuery("""
            insert into EMP (EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, COMM, DEPTNO)
            values  (7839, 'KING', 'PRESIDENT', null, DATE '1981-11-17', 5000.00, null, 10),
                    (7698, 'BLAKE', 'MANAGER', 7839, DATE '1981-05-01', 2850.00, null, 30),
                    (7782, 'CLARK', 'MANAGER', 7839, DATE '1981-06-09', 2450.00, null, 10),
                    (7566, 'JONES', 'MANAGER', 7839, DATE '1981-04-02', 2975.00, null, 20),
                    (7788, 'SCOTT', 'ANALYST', 7566, DATE '1987-04-19', 3000.00, null, 20),
                    (7902, 'FORD', 'ANALYST', 7566, DATE '1981-12-03', 3000.00, null, 20),
                    (7369, 'SMITH', 'CLERK', 7902, DATE '1980-12-17', 800.00, null, 20),
                    (7499, 'ALLEN', 'SALESMAN', 7698, DATE '1981-02-20', 1600.00, 300.00, 30),
                    (7521, 'WARD', 'SALESMAN', 7698, DATE '1981-02-22', 1250.00, 500.00, 30),
                    (7654, 'MARTIN', 'SALESMAN', 7698, DATE '1981-09-28', 1250.00, 1400.00, 30),
                    (7844, 'TURNER', 'SALESMAN', 7698, DATE '1981-09-08', 1500.00, 0.00, 30),
                    (7876, 'ADAMS', 'CLERK', 7788, DATE '1987-05-23', 1100.00, null, 20),
                    (7900, 'JAMES', 'CLERK', 7698, DATE '1981-12-03', 950.00, null, 30),
                    (7934, 'MILLER', 'CLERK', 7782, DATE '1982-01-23', 1300.00, null, 10)
            """, 0, 0);
    }

    @Test
    void executesOracleJsonQuery() throws IOException {
        var model = tableDetail("EMP");

        assertNotNull(model.jsonQuerySql());
        var result = dataSourceService.executeQuery(model.jsonQuerySql(), 0, 10);

        assertEquals(14, result.total());
        assertEquals(List.of("JSON"), result.cols());
        assertJsonColumn(result);
        var king = findJsonRow(result, "ENAME", "KING");
        assertEquals(7839, king.get("EMPNO"));
        assertEquals(10, king.get("DEPTNO"));
    }

    @Test
    void executesOracleRelationshipJsonQueryForOutgoingRelationship() throws IOException {
        var model = tableDetail("EMP");

        assertNotNull(model.relationshipJsonSql());
        var result = dataSourceService.executeQuery(model.relationshipJsonSql(), 0, 10);

        assertEquals(14, result.total());
        assertEquals(List.of("JSON"), result.cols());
        assertJsonColumn(result);
        var jones = findJsonRow(result, "ENAME", "JONES");
        assertEquals(7566, jones.get("EMPNO"));
        assertEquals(20, jones.get("DEPTNO"));

        var department = assertInstanceOf(Map.class, jones.get(DEPT_RELATIONSHIP_NAME));
        assertEquals(20, department.get("DEPTNO"));
        assertEquals("RESEARCH", department.get("DNAME"));

        var manager = assertInstanceOf(Map.class, jones.get(MANAGER_RELATIONSHIP_NAME));
        assertEquals(7839, manager.get("EMPNO"));
        assertEquals("KING", manager.get("ENAME"));

        var reports = assertInstanceOf(List.class, jones.get(REPORTS_RELATIONSHIP_NAME));
        assertEquals(2, reports.size());
        assertTrue(containsEmployee(reports, "SCOTT", 20));
        assertTrue(containsEmployee(reports, "FORD", 20));
    }

    @Test
    void executesOracleRelationshipJsonQueryForIncomingRelationship() throws IOException {
        var model = tableDetail("DEPT");

        assertNotNull(model.relationshipJsonSql());
        var result = dataSourceService.executeQuery(model.relationshipJsonSql(), 0, 10);

        assertEquals(4, result.total());
        assertEquals(List.of("JSON"), result.cols());
        assertJsonColumn(result);
        var research = findJsonRow(result, "DNAME", "RESEARCH");
        assertEquals(20, research.get("DEPTNO"));

        var relationship = assertInstanceOf(List.class, research.get(DEPT_RELATIONSHIP_NAME));
        assertEquals(5, relationship.size());
        assertTrue(containsEmployee(relationship, "JONES", 20));
        assertTrue(containsEmployee(relationship, "SCOTT", 20));
        assertTrue(containsEmployee(relationship, "FORD", 20));
        assertTrue(containsEmployee(relationship, "SMITH", 20));
        assertTrue(containsEmployee(relationship, "ADAMS", 20));
    }

    private void dropTable(String tableName) {
        try {
            dataSourceService.executeQuery("DROP TABLE " + tableName, 0, 0);
        } catch (RuntimeException _) {
            // Oracle has no DROP TABLE IF EXISTS.
        }
    }

    private DataSourceController.TableDetail tableDetail(String tableName) {
        var panel = new DataSourceControlPanel(
            DATA_SOURCE,
            dataSourceService,
            environment,
            new ControlPanelConfiguration(DataSourceControlPanel.NAME)
        );
        var table = panel.getBody().tables().stream()
            .filter(candidate -> candidate.name().equals(tableName))
            .findFirst()
            .orElseThrow();
        var beanLocator = mock(BeanLocator.class);
        doReturn(Map.of(DATA_SOURCE, dataSourceService)).when(beanLocator).mapOfType(SERVICE_ARGUMENT);
        doReturn(Map.of(DATA_SOURCE, panel)).when(beanLocator).mapOfType(PANEL_ARGUMENT);
        var renderedModel = new AtomicReference<Object>();
        var renderer = mock(ControlPanelRenderer.class);
        doAnswer(invocation -> {
            renderedModel.set(invocation.getArgument(1));
            return "";
        }).when(renderer).render(any(), any());
        var controller = new DataSourceController(beanLocator, jsonMapper, renderer);
        var response = controller.tableDetail(DATA_SOURCE, table.schema(), table.name());
        assertEquals(HttpResponse.ok().getStatus(), response.getStatus());
        return tableDetail(response, renderedModel.get());
    }

    private DataSourceController.TableDetail tableDetail(HttpResponse<?> response, Object renderedModel) {
        assertEquals("", response.body());
        return assertInstanceOf(DataSourceController.TableDetail.class, renderedModel);
    }

    private Map<String, Object> findJsonRow(DataSourceService.QueryResult result, String key, String value) throws IOException {
        for (List<String> row : result.rows()) {
            assertFalse(row.isEmpty());
            var json = jsonMapper.readValue(row.getFirst().getBytes(), Argument.mapOf(String.class, Object.class));
            if (value.equals(json.get(key))) {
                return json;
            }
        }
        throw new AssertionError("No JSON row found for " + key + ": " + value);
    }

    private static void assertJsonColumn(DataSourceService.QueryResult result) {
        assertEquals(1, result.columns().size());
        assertEquals("JSON", result.columns().getFirst().typeName().toUpperCase(Locale.ROOT));
    }

    private static boolean containsEmployee(List<?> rows, String name, int departmentId) {
        for (Object row : rows) {
            var employee = assertInstanceOf(Map.class, row);
            if (name.equals(employee.get("ENAME")) && Integer.valueOf(departmentId).equals(employee.get("DEPTNO"))) {
                return true;
            }
        }
        return false;
    }
}
