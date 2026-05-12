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
package io.micronaut.controlpanel.panels.r2dbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class R2dbcControlPanelTest {

    @Test
    void createsOnePanelPerConnectionFactoryBean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.control-panel.panels.r2dbc.enabled", true
        ), Environment.TEST)) {
            context.registerSingleton(ConnectionFactory.class, connectionFactory("H2"), io.micronaut.inject.qualifiers.Qualifiers.byName("default"));
            context.registerSingleton(ConnectionFactory.class, connectionFactory("PostgreSQL"), io.micronaut.inject.qualifiers.Qualifiers.byName("analytics"));

            assertEquals(2, context.getBeansOfType(R2dbcControlPanel.class).size());
            assertTrue(context.getBeansOfType(R2dbcControlPanel.class).stream().anyMatch(panel -> panel.getName().equals("r2dbc-default")));
            assertTrue(context.getBeansOfType(R2dbcControlPanel.class).stream().anyMatch(panel -> panel.getName().equals("r2dbc-analytics")));
        }
    }

    @Test
    void exposesExpectedPanelMetadata() {
        R2dbcDiagnosticsService service = mock(R2dbcDiagnosticsService.class);
        when(service.getBeanName()).thenReturn("default");
        R2dbcControlPanel panel = new R2dbcControlPanel(service, new ControlPanelConfiguration(R2dbcControlPanel.NAME));

        assertEquals("r2dbc-default", panel.getName());
        assertEquals("/views/r2dbc/body", panel.getBodyView().file());
        assertEquals("/views/r2dbc/detail", panel.getDetailedView().file());
        assertEquals("R2DBC", panel.getCategory().name());
        assertEquals("Diagnostics", panel.getDetailLinkName());
    }

    @Test
    void honorsStandardPanelDisabledConfiguration() {
        R2dbcDiagnosticsService service = mock(R2dbcDiagnosticsService.class);
        when(service.getBeanName()).thenReturn("default");
        ControlPanelConfiguration configuration = new ControlPanelConfiguration(R2dbcControlPanel.NAME);
        configuration.setEnabled(false);

        R2dbcControlPanel panel = new R2dbcControlPanel(service, configuration);

        assertFalse(panel.isEnabled());
    }

    @Test
    void doesNotCreatePanelWhenDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.control-panel.panels.r2dbc.enabled", false
        ), Environment.TEST)) {
            context.registerSingleton(ConnectionFactory.class, connectionFactory("H2"), io.micronaut.inject.qualifiers.Qualifiers.byName("default"));

            assertTrue(context.getBeansOfType(R2dbcControlPanel.class).isEmpty());
        }
    }

    private static ConnectionFactory connectionFactory(String metadataName) {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.getMetadata()).thenReturn(() -> metadataName);
        return factory;
    }
}
