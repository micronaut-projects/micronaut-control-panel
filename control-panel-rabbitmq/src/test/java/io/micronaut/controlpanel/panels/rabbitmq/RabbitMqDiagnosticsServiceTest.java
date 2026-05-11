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
package io.micronaut.controlpanel.panels.rabbitmq;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqBody;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.messaging.Acknowledgement;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.intercept.RabbitMQConsumerAdvice;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqDiagnosticsServiceTest {

    private static final String SPEC_NAME = "RabbitMqDiagnosticsServiceTest";

    @Test
    void panelReportsListenerConnectionAndEmptyMetricDiagnostics() {
        try (ApplicationContext context = ApplicationContext.run(Map.ofEntries(
            Map.entry("spec.name", SPEC_NAME),
            Map.entry("test.queue", "orders.created"),
            Map.entry("test.connection", "primary"),
            Map.entry("test.executor", "io"),
            Map.entry("test.consumers", "3"),
            Map.entry("rabbitmq.servers.primary.host", "localhost"),
            Map.entry("rabbitmq.servers.primary.port", 5673),
            Map.entry("rabbitmq.servers.primary.username", "developer"),
            Map.entry("rabbitmq.servers.primary.password", "secret"),
            Map.entry("rabbitmq.servers.primary.virtual-host", "/orders"),
            Map.entry("rabbitmq.servers.primary.channel-pool.max-idle-channels", 7),
            Map.entry("micronaut.control-panel.panels.rabbitmq.management-url", "http://localhost:15672")
        ))) {
            RabbitMqControlPanel panel = context.getBean(RabbitMqControlPanel.class);
            RabbitMqBody body = panel.getBody();

            assertEquals("RabbitMQ", panel.getTitle());
            assertEquals("1", panel.getBadge());
            assertEquals("http://localhost:15672", body.managementUrl());
            assertEquals(1, body.listenerCount());
            assertEquals(1, body.connectionCount());
            assertFalse(body.metricsPresent());
            assertEquals("No RabbitMQ Micrometer meters were found. Enable Micrometer and the RabbitMQ metrics binder to populate this section.", body.metrics().message());

            var listener = body.listeners().get(0);
            assertEquals("OrderListener", listener.beanSimpleName());
            assertEquals("receive", listener.method());
            assertEquals("orders.created", listener.queue());
            assertEquals("primary", listener.connection());
            assertEquals("io", listener.executor());
            assertEquals("3", listener.numberOfConsumers());
            assertEquals(25, listener.prefetch());
            assertTrue(listener.exclusive());
            assertTrue(listener.reQueue());
            assertFalse(listener.autoAcknowledgment());
            assertTrue(listener.acknowledgementArgument());

            var connection = body.connections().get(0);
            assertEquals("primary", connection.name());
            assertEquals("localhost", connection.host());
            assertEquals(5673, connection.port());
            assertEquals("/orders", connection.virtualHost());
            assertEquals(RabbitMqRedactor.REDACTED, connection.username());
            assertEquals("7", connection.maxIdleChannels());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            RabbitMqControlPanel.ENABLED_PROPERTY, false
        ))) {
            ControlPanelConfiguration configuration = context.getBean(ControlPanelConfiguration.class, Qualifiers.byName(RabbitMqControlPanel.NAME));
            assertFalse(configuration.isEnabled());
            assertFalse(context.containsBean(RabbitMqControlPanel.class));
        }
    }

    @Test
    void invalidManagementUrlIsNotDisplayed() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "test.queue", "orders.created",
            "micronaut.control-panel.panels.rabbitmq.management-url", "file:///tmp/rabbitmq"
        ))) {
            RabbitMqBody body = context.getBean(RabbitMqControlPanel.class).getBody();

            assertNotNull(body);
            assertEquals(null, body.managementUrl());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Primary
    static class TestRabbitMqHealthResolver implements RabbitMqHealthResolver {

        @Override
        public io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo resolve() {
            return new io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo(false, "Unavailable", "Test health unavailable.", List.of());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Primary
    static class TestRabbitMqMetricsResolver implements RabbitMqMetricsResolver {

        @Override
        public io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo resolve() {
            return new io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo(true, "No RabbitMQ Micrometer meters were found. Enable Micrometer and the RabbitMQ metrics binder to populate this section.", "rabbitmq", List.of());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Replaces(RabbitMQConsumerAdvice.class)
    static class NoopRabbitConsumerProcessor implements ExecutableMethodProcessor<Queue> {

        @Override
        public <T> void process(BeanDefinition<T> beanDefinition, ExecutableMethod<T, ?> method) {
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @RabbitListener(connection = "${test.connection:default}", executor = "${test.executor:}")
    static class OrderListener {

        @Queue(value = "orders.created", numberOfConsumers = "3", prefetch = 25, exclusive = true, reQueue = true)
        void receive(String body, Acknowledgement acknowledgement) {
        }
    }
}
