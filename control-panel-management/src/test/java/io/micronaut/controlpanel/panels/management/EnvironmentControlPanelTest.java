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
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of("endpoints.env.enabled", true))) {
            EnvironmentControlPanel panel = ctx.getBean(EnvironmentControlPanel.class);
            assertEquals("Environment Properties", panel.getTitle());
            assertEquals("fa-sliders", panel.getIcon());
            assertEquals(10, panel.getOrder());
            assertEquals("Browse properties", panel.getDetailLinkName());
            @SuppressWarnings("unchecked")
            var body = (java.util.Map<String, Object>) panel.getBody();
            @SuppressWarnings("unchecked")
            var activeEnvironments = (java.util.List<String>) body.get("activeEnvironments");
            assertEquals(java.util.List.of(Environment.TEST), activeEnvironments);
            @SuppressWarnings("unchecked")
            var packages = (java.util.Collection<String>) body.get("packages");
            assertNotNull(packages);
            assertFalse(packages.isEmpty());
            @SuppressWarnings("unchecked")
            var propertySources = (java.util.Collection<Object>) body.get("propertySources");
            assertEquals(4, propertySources.size());
        }
    }
}
