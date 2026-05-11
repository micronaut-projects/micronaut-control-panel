/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.neo4j;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jBody;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jConnectionInfo;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jDiagnostic;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.ServerInfo;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Neo4jDiagnosticsServiceTest {

    @Test
    void resolvesConnectedDriverServerInfoAndBoundedMetadata() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result serverResult = serverResult();
        Result labelResult = metadataResult("label", "Movie", "Person");
        Result relationshipTypeResult = metadataResult("relationshipType", "ACTED_IN");
        Result propertyKeyResult = metadataResult("propertyKey", "title", "released");
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.run("RETURN 1 AS ok")).thenReturn(serverResult);
        when(session.run(eq("CALL db.labels() YIELD label RETURN label ORDER BY label LIMIT $limit"), anyMap()))
            .thenReturn(labelResult);
        when(session.run(eq("CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType ORDER BY relationshipType LIMIT $limit"), anyMap()))
            .thenReturn(relationshipTypeResult);
        when(session.run(eq("CALL db.propertyKeys() YIELD propertyKey RETURN propertyKey ORDER BY propertyKey LIMIT $limit"), anyMap()))
            .thenReturn(propertyKeyResult);

        Neo4jBody body = service(driver).getBody();

        assertTrue(body.connected());
        assertEquals("Neo4j/5.26", body.server().agent());
        assertEquals("localhost:7687", body.server().address());
        assertEquals("5.4", body.server().protocolVersion());
        assertEquals(List.of("Movie", "Person"), body.labels());
        assertEquals(List.of("ACTED_IN"), body.relationshipTypes());
        assertEquals(List.of("title", "released"), body.propertyKeys());
        assertTrue(body.diagnostics().isEmpty());
    }

    @Test
    void connectivityFailureBecomesNonFatalDiagnostic() {
        Driver driver = mock(Driver.class);
        doThrow(new ServiceUnavailableException("bolt://user:secret@localhost failed")).when(driver).verifyConnectivity();

        Neo4jBody body = service(driver).getBody();

        assertFalse(body.connected());
        assertEquals("Unavailable", body.status().label());
        assertEquals(1, body.diagnostics().size());
        assertEquals("Connectivity", body.diagnostics().get(0).area());
        assertFalse(body.toString().contains("secret"));
    }

    @Test
    void permissionFailureHidesOnlyThatMetadataSection() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result serverResult = serverResult();
        Result relationshipTypeResult = metadataResult("relationshipType", "ACTED_IN");
        Result propertyKeyResult = metadataResult("propertyKey", "name");
        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(session.run("RETURN 1 AS ok")).thenReturn(serverResult);
        when(session.run(eq("CALL db.labels() YIELD label RETURN label ORDER BY label LIMIT $limit"), anyMap()))
            .thenThrow(new ClientException("Neo.ClientError.Security.AuthorizationExpired", "permission denied"));
        when(session.run(eq("CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType ORDER BY relationshipType LIMIT $limit"), anyMap()))
            .thenReturn(relationshipTypeResult);
        when(session.run(eq("CALL db.propertyKeys() YIELD propertyKey RETURN propertyKey ORDER BY propertyKey LIMIT $limit"), anyMap()))
            .thenReturn(propertyKeyResult);

        Neo4jBody body = service(driver).getBody();

        assertTrue(body.connected());
        assertTrue(body.labels().isEmpty());
        assertEquals(List.of("ACTED_IN"), body.relationshipTypes());
        assertEquals(1, body.diagnostics().size());
        assertTrue(body.diagnostics().get(0).permission());
        assertTrue(body.diagnostics().get(0).message().contains("permission"));
    }

    @Test
    void sanitizesAuthenticationMessages() {
        Neo4jDiagnostic diagnostic = Neo4jDiagnosticsService.toDiagnostic(
            "Connectivity",
            new AuthenticationException("Neo.ClientError.Security.Unauthorized", "password hunter2")
        );

        assertEquals("Authentication failed while connecting to Neo4j.", diagnostic.message());
        assertFalse(diagnostic.message().contains("hunter2"));
    }

    private static Neo4jDiagnosticsService service(Driver driver) {
        Neo4jPanelConfiguration configuration = new Neo4jPanelConfiguration();
        Neo4jConnectionSummaryResolver resolver = mock(Neo4jConnectionSummaryResolver.class);
        when(resolver.resolve()).thenReturn(new Neo4jConnectionInfo("default", "bolt://localhost:7687", "neo4j", "", ""));
        return new Neo4jDiagnosticsService("default", driver, configuration, resolver);
    }

    private static Result serverResult() {
        Result result = mock(Result.class);
        ResultSummary summary = mock(ResultSummary.class);
        ServerInfo server = mock(ServerInfo.class);
        when(server.agent()).thenReturn("Neo4j/5.26");
        when(server.address()).thenReturn("localhost:7687");
        when(server.protocolVersion()).thenReturn("5.4");
        when(summary.server()).thenReturn(server);
        when(result.consume()).thenReturn(summary);
        return result;
    }

    private static Result metadataResult(String column, String... values) {
        Result result = mock(Result.class);
        when(result.list(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<Record, String> mapper = invocation.getArgument(0, Function.class);
            return List.of(values).stream()
                .map(value -> record(column, value))
                .map(mapper)
                .toList();
        });
        return result;
    }

    private static Record record(String column, String value) {
        Record record = mock(Record.class);
        Value neo4jValue = Values.value(value);
        when(record.get(column)).thenReturn(neo4jValue);
        return record;
    }

    @Test
    void noDriverPanelIsAvailableWhenNoDriverBeansExist() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            Neo4jNoDriversControlPanel panel = context.getBean(Neo4jNoDriversControlPanel.class);

            assertEquals("Neo4j", panel.getTitle());
            assertEquals("neo4j", panel.getName());
            assertFalse(panel.hasDetails());
            assertTrue(panel.getBody().noDriverBeans());
        }
    }

    @Test
    void metadataLimitsAreCapped() {
        Neo4jPanelConfiguration configuration = new Neo4jPanelConfiguration();

        configuration.setMaxLabels(Integer.MAX_VALUE);
        configuration.setMaxRelationshipTypes(Integer.MAX_VALUE);
        configuration.setMaxPropertyKeys(Integer.MAX_VALUE);

        assertEquals(Neo4jPanelConfiguration.MAX_METADATA_LIMIT, configuration.getMaxLabels());
        assertEquals(Neo4jPanelConfiguration.MAX_METADATA_LIMIT, configuration.getMaxRelationshipTypes());
        assertEquals(Neo4jPanelConfiguration.MAX_METADATA_LIMIT, configuration.getMaxPropertyKeys());
    }
}
