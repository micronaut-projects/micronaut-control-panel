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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.logging.LogLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggersControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of("endpoints.loggers.enabled", true))) {
            LoggersControlPanel panel = ctx.getBean(LoggersControlPanel.class);
            assertEquals("Loggers", panel.getTitle());
            assertEquals("fa-file-lines", panel.getIcon());
            assertEquals(40, panel.getOrder());
            assertEquals(LogLevel.values().length, panel.getBody().levels().size());
            assertEquals(2, panel.getBody().loggers().keySet().size());
            assertEquals(LogLevel.INFO, panel.getBody().loggers().get("ROOT").get("configuredLevel"));
            assertEquals(LogLevel.DEBUG, panel.getBody().loggers().get("io.micronaut.controlpanel").get("configuredLevel"));
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(LoggersControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(LoggersControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(LoggersControlPanel.class));
        }
    }
}
