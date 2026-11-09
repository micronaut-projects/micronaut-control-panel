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
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jControlPanelTest {

    @Test
    void showsServerDatabasesAndGraph() {
        // Neo4j is started by Micronaut Test Resources, see application-test.yml
        try (ApplicationContext context = ApplicationContext.run()) {
            Neo4jControlPanel panel = context.getBean(Neo4jControlPanel.class);
            assertEquals("Neo4j", panel.getTitle());
            assertEquals("datasource", panel.getCategory().id());

            Neo4jInfo body = panel.getBody();
            assertTrue(body.uri().startsWith("bolt://"), body.uri());
            assertEquals("none", body.authentication());
            assertNull(body.server(), "the dashboard card does not query Neo4j");
            assertFalse(body.driverOpen());

            context.getBean(Driver.class).executableQuery(
                "CREATE (:Customer {name: 'Ada'})-[:PLACED]->(:Order {total: 42}), (:Customer {name: 'Grace'})").execute();

            Neo4jInfo detailed = panel.getDetailedBody();
            assertNull(detailed.error(), detailed.error());
            assertTrue(detailed.driverOpen());
            assertNotNull(detailed.server());
            assertTrue(detailed.server().version().startsWith("5."), detailed.server().version());
            assertEquals("community", detailed.server().edition());
            assertTrue(detailed.databases().stream().anyMatch(database -> database.name().equals("neo4j") && database.isDefault()), detailed.databases().toString());
            assertNotNull(detailed.graph());
            assertEquals(3, detailed.graph().nodes());
            assertEquals(1, detailed.graph().relationships());
            assertEquals(List.of("Customer", "Order"), detailed.graph().labels());
            assertEquals(List.of("PLACED"), detailed.graph().relationshipTypes());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(Neo4jControlPanel.NAME).isPresent());
        }
    }

    @Test
    void unreachableServerIsReportedByErrorType() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("neo4j.uri", "bolt://neo4j:s3cret@127.0.0.1:1"))) {
            Neo4jInfo detailed = context.getBean(Neo4jControlPanel.class).getDetailedBody();
            assertNull(detailed.server());
            assertNotNull(detailed.error());
            assertFalse(detailed.error().contains("s3cret"), detailed.error());
            assertEquals("bolt://127.0.0.1:1", detailed.uri());
            assertEquals("configured in the URI", detailed.authentication());
        }
    }

    @Test
    void uriIsShownWithoutCredentials() {
        assertEquals("neo4j://cluster:7687", Neo4jInspector.sanitize(URI.create("neo4j://user:pass@cluster:7687?policy=eu")));
        assertEquals("(hidden)", Neo4jInspector.sanitize(URI.create("bolt://neo4j:p@ssw0rd@localhost:7687")));
        assertEquals("(hidden)", Neo4jInspector.sanitize(URI.create("bolt://user:secret@my_host:7687")));
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(Neo4jControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(Neo4jControlPanel.class));
        }
    }
}
