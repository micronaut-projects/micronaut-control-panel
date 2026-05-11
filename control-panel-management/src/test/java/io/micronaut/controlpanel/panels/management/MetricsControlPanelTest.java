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
package io.micronaut.controlpanel.panels.management;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class MetricsControlPanelTest {

    @Test
    void itIsConfiguredCorrectlyWhenMetricsEndpointIsAvailable() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.metrics.enabled", true))) {
            registerTestMetrics(ctx.getBean(MeterRegistry.class));

            MetricsControlPanel panel = ctx.getBean(MetricsControlPanel.class);

            assertEquals("Metrics", panel.getTitle());
            assertEquals("fa-chart-line", panel.getIcon());
            assertEquals(20, panel.getOrder());
            assertTrue(panel.getBody().names().contains("control.panel.test.requests"));
            assertEquals(panel.getBody().names().size(), Integer.parseInt(panel.getBadge()));
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.metrics.enabled", true,
            MetricsControlPanel.ENABLED_PROPERTY, false
        ))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(MetricsControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(MetricsControlPanel.class));
            assertFalse(ctx.containsBean(MetricsControlPanelController.class));
        }
    }

    @Test
    void detailApiPreservesRepeatableTagValuesContainingColon() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.metrics.enabled", true))) {
            registerTestMetrics(ctx.getBean(MeterRegistry.class));
            MetricsControlPanelController controller = ctx.getBean(MetricsControlPanelController.class);

            var response = controller.detail("control.panel.test.requests", List.of("outcome:success:demo"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.body();

            assertEquals("control.panel.test.requests", body.get("name"));
            @SuppressWarnings("unchecked")
            var measurements = (List<Map<String, Object>>) body.get("measurements");
            assertFalse(measurements.isEmpty());
            @SuppressWarnings("unchecked")
            var tags = (List<Map<String, Object>>) body.get("availableTags");
            assertTrue(tags.stream().anyMatch(tag -> Objects.equals(tag.get("tag"), "outcome")));
        }
    }

    @Test
    void metricNamesAreNotInterpolatedIntoInlineJavaScript() throws IOException {
        try (var in = getClass().getResourceAsStream("/views/metrics/detail.hbs")) {
            assertNotNull(in);
            var template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var script = template.substring(template.indexOf("<script>"));

            assertFalse(script.contains("{{this}}"));
            assertFalse(script.contains("{{#each names}}"));
        }
    }

    @Test
    void metricSelectorUsesPagedDataTable() throws IOException {
        try (var in = getClass().getResourceAsStream("/views/metrics/detail.hbs")) {
            assertNotNull(in);
            var template = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("data-filter-table-page-size=\"12\""));
            assertTrue(template.contains("data-filter-table-row"));
            assertTrue(template.contains("data-filter-table-page-size-select"));
            assertFalse(template.contains("class=\"list-group\""));
        }
    }

    private static void registerTestMetrics(MeterRegistry registry) {
        Counter.builder("control.panel.test.requests")
            .description("Deterministic test requests")
            .baseUnit("requests")
            .tag("outcome", "success:demo")
            .register(registry)
            .increment(3);
        Counter.builder("control.panel.test.\"quoted\\\\metric")
            .description("Metric with quoted and backslashed name")
            .tag("tag\"key", "value\\with\nnewline")
            .register(registry)
            .increment();
    }
}
