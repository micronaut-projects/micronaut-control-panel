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
package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayControlPanelTest {

    @Test
    void flywayPanelIsPresentWhenFlywayIsOnTheClasspath() {
        try (ApplicationContext context = ApplicationContext.run()) {
            FlywayControlPanel panel = context.getBean(FlywayControlPanel.class);

            assertEquals(FlywayControlPanel.NAME, panel.getName());
            assertEquals("Migrations", panel.getDetailLinkName());
            assertEquals("0", panel.getBadge());
            assertFalse(panel.getBody().hasConfigurations());
        }
    }

    @Test
    void flywayPanelUsesConfiguredEnabledFlag() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.control-panel.panels.flyway.enabled", "false"
        ))) {
            ControlPanel<?> panel = context.getBean(FlywayControlPanel.class);

            assertFalse(panel.isEnabled());
        }
    }

    @Test
    void flywayPanelUsesDataSourceCategory() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ControlPanel.Category category = context.getBean(FlywayControlPanel.class).getCategory();

            assertEquals("datasource", category.id());
            assertEquals("Data Sources", category.name());
            assertTrue(category.iconClass().contains("database"));
        }
    }
}
