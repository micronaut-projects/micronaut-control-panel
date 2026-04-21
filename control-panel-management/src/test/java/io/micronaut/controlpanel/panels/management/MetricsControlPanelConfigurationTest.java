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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsControlPanelConfigurationTest {

    @Test
    void itRequiresTheMetricsEndpoint() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(
            "endpoints.metrics.enabled", true,
            "endpoints.metrics.sensitive", false
        ))) {
            assertTrue(ctx.containsBean(MetricsControlPanel.class));
            assertTrue(ctx.containsBean(MetricsControlPanelController.class));
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(
            MetricsControlPanel.ENABLED_PROPERTY, false,
            "endpoints.metrics.enabled", true,
            "endpoints.metrics.sensitive", false
        ))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(MetricsControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(MetricsControlPanel.class));
            assertFalse(ctx.containsBean(MetricsControlPanelController.class));
        }
    }
}
