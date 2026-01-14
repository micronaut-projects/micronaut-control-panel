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
package io.micronaut.controlpanel.panels.management.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeansControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            BeansControlPanel panel = ctx.getBean(BeansControlPanel.class);
            assertEquals("Bean Definitions", panel.getTitle());
            assertEquals("fa-plug", panel.getIcon());
            assertEquals(0, panel.getOrder());
            assertNotNull(panel.getBody().micronautBeansByPackage());
            assertNotNull(panel.getBody().otherBeansByPackage());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(BeansControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(BeansControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(BeansControlPanel.class));
        }
    }

    @Test
    void itHandlesPrimitiveTypeBeansCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ctx.registerSingleton(int.class, 42);
            BeansControlPanel panel = ctx.getBean(BeansControlPanel.class);
            assertNotNull(panel);
            assertNotNull(panel.getBody().micronautBeansByPackage());
            assertNotNull(panel.getBody().otherBeansByPackage());
            assertTrue(panel.getBody().otherBeansByPackage().containsKey("primitive"));
        }
    }
}
