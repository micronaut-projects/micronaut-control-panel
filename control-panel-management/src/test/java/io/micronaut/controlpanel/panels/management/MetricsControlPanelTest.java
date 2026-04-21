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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetricsControlPanelTest {

    @Test
    void itBuildsAnOrderedPreviewAndBadge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "web")
            .register(registry)
            .increment(2);
        Counter.builder("http.server.requests")
            .description("HTTP server requests")
            .tag("method", "GET")
            .register(registry)
            .increment();

        MetricsControlPanel panel = new MetricsControlPanel(
            new MetricsEndpoint(registry, new javax.sql.DataSource[0]),
            configuration()
        );

        MetricsControlPanel.Body body = panel.getBody();

        assertEquals("Metrics", panel.getTitle());
        assertEquals("fa-chart-line", panel.getIcon());
        assertEquals(35, panel.getOrder());
        assertEquals("2", panel.getBadge());
        assertIterableEquals(
            java.util.List.of("example.orders.processed", "http.server.requests"),
            body.metricNames()
        );
        assertIterableEquals(body.metricNames(), body.previewMetricNames());
        assertEquals("example.orders.processed", body.initialMetricName());
    }

    @Test
    void itHandlesAnEmptyRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsControlPanel panel = new MetricsControlPanel(
            new MetricsEndpoint(registry, new javax.sql.DataSource[0]),
            configuration()
        );

        MetricsControlPanel.Body body = panel.getBody();

        assertEquals("0", panel.getBadge());
        assertIterableEquals(java.util.List.of(), body.metricNames());
        assertIterableEquals(java.util.List.of(), body.previewMetricNames());
        assertNull(body.initialMetricName());
    }

    private static ControlPanelConfiguration configuration() {
        ControlPanelConfiguration configuration = new ControlPanelConfiguration(MetricsControlPanel.NAME);
        configuration.setTitle("Metrics");
        configuration.setIcon("fa-chart-line");
        configuration.setOrder(35);
        return configuration;
    }
}
