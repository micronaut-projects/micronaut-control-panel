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
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsControlPanelControllerTest {

    @Test
    void itReturnsKnownMetricDetails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "web")
            .tag("status", "success")
            .register(registry)
            .increment(4);
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "batch")
            .tag("status", "failure")
            .register(registry)
            .increment();

        MetricsControlPanelController controller = new MetricsControlPanelController(registry);

        var response = controller.detail("example.orders.processed", null);

        assertEquals(HttpStatus.OK, response.status());
        assertNotNull(response.body());
        assertEquals("example.orders.processed", response.body().name());
        assertEquals("Processed example orders", response.body().description());
        assertEquals("orders", response.body().baseUnit());
        assertIterableEquals(java.util.List.of(), response.body().selectedTags());
        assertEquals(1, response.body().measurements().size());
        assertEquals("COUNT", response.body().measurements().get(0).statistic());
        assertEquals(2, response.body().availableTags().size());
        assertEquals("channel", response.body().availableTags().get(0).tag());
        assertIterableEquals(java.util.List.of("batch", "web"), response.body().availableTags().get(0).values());
    }

    @Test
    void itAppliesTagFilters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "web")
            .tag("status", "success")
            .register(registry)
            .increment(4);
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("channel", "batch")
            .tag("status", "failure")
            .register(registry)
            .increment();

        MetricsControlPanelController controller = new MetricsControlPanelController(registry);

        var response = controller.detail("example.orders.processed", java.util.List.of("channel:web"));

        assertEquals(HttpStatus.OK, response.status());
        assertNotNull(response.body());
        assertIterableEquals(java.util.List.of("channel:web"), response.body().selectedTags());
        assertEquals(1, response.body().availableTags().size());
        assertEquals("status", response.body().availableTags().get(0).tag());
        assertIterableEquals(java.util.List.of("success"), response.body().availableTags().get(0).values());
        assertEquals(4d, response.body().measurements().get(0).value());
    }

    @Test
    void itReturnsNotFoundForUnknownMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsControlPanelController controller = new MetricsControlPanelController(registry);

        var response = controller.detail("unknown.metric", null);

        assertEquals(HttpStatus.NOT_FOUND, response.status());
    }

    @Test
    void itReportsInvalidTagsAgainstTheTagQueryArgument() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsControlPanelController controller = new MetricsControlPanelController(registry);

        UnsatisfiedArgumentException exception = assertThrows(
            UnsatisfiedArgumentException.class,
            () -> controller.detail("example.orders.processed", java.util.List.of("invalid-tag"))
        );

        assertEquals("tag", exception.getArgument().getName());
    }

    @Test
    void itAppliesTagFiltersWithColonSeparatedValues() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("region", "us:east")
            .tag("status", "success")
            .register(registry)
            .increment(3);
        Counter.builder("example.orders.processed")
            .description("Processed example orders")
            .baseUnit("orders")
            .tag("region", "eu:west")
            .tag("status", "success")
            .register(registry)
            .increment();

        MetricsControlPanelController controller = new MetricsControlPanelController(registry);

        var response = controller.detail("example.orders.processed", java.util.List.of("region:us:east"));

        assertEquals(HttpStatus.OK, response.status());
        assertNotNull(response.body());
        assertIterableEquals(java.util.List.of("region:us:east"), response.body().selectedTags());
        assertEquals(1, response.body().availableTags().size());
        assertEquals("status", response.body().availableTags().get(0).tag());
        assertIterableEquals(java.util.List.of("success"), response.body().availableTags().get(0).values());
        assertEquals(3d, response.body().measurements().get(0).value());
    }
}
