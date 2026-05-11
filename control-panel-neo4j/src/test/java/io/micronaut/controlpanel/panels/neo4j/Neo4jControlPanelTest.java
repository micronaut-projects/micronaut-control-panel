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
import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class Neo4jControlPanelTest {

    @Test
    void contextCreatesPanelForEachDriverBeanAndDoesNotExposePasswordInBody() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            Driver driver = mock(Driver.class);
            doThrow(new RuntimeException("configured-secret")).when(driver).verifyConnectivity();
            context.registerSingleton(Driver.class, driver, Qualifiers.byName("default"));

            var panels = context.getBeansOfType(Neo4jControlPanel.class);

            assertEquals(1, panels.size());
            Neo4jControlPanel panel = panels.iterator().next();
            assertEquals("default", panel.getTitle());
            assertEquals("neo4j-default", panel.getName());
            String body = panel.getBody().toString();
            assertFalse(body.contains("configured-secret"));
            assertFalse(body.contains("password"));
        }
    }

    @Test
    void contextCreatesPanelForMicronautNeo4jDriverBean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "neo4j.uri", "bolt://localhost:7687",
            "neo4j.username", "neo4j",
            "neo4j.password", "configured-secret"
        ))) {
            var panels = context.getBeansOfType(Neo4jControlPanel.class);

            assertEquals(1, panels.size());
            Neo4jControlPanel panel = panels.iterator().next();
            assertEquals("neo4j-default", panel.getName());
            assertFalse(panel.getBody().toString().contains("configured-secret"));
        }
    }

    @Test
    void panelMetadataIsConfigured() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        Neo4jPanelConfiguration panelConfiguration = new Neo4jPanelConfiguration();
        var configuration = mock(io.micronaut.controlpanel.core.config.ControlPanelConfiguration.class);

        Neo4jControlPanel panel = new Neo4jControlPanel("default", beanContext, environment, panelConfiguration, configuration);

        assertEquals("default", panel.getTitle());
        assertEquals("neo4j-default", panel.getName());
        assertEquals("Neo4j", panel.getCategory().name());
        assertEquals("fas fa-circle-nodes", panel.getIcon());
    }

    @Test
    void badgeDoesNotProbeNeo4j() {
        BeanContext beanContext = mock(BeanContext.class);
        Environment environment = mock(Environment.class);
        Neo4jPanelConfiguration panelConfiguration = new Neo4jPanelConfiguration();
        var configuration = mock(io.micronaut.controlpanel.core.config.ControlPanelConfiguration.class);
        Neo4jControlPanel panel = new Neo4jControlPanel("default", beanContext, environment, panelConfiguration, configuration);

        assertEquals("", panel.getBadge());
        verifyNoInteractions(beanContext, environment);
    }
}
