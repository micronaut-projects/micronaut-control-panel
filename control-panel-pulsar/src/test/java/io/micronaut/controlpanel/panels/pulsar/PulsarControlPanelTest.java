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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.type.Argument;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import io.micronaut.pulsar.PulsarProducerRegistry;
import io.micronaut.pulsar.PulsarReaderRegistry;
import io.micronaut.pulsar.events.ConsumerSubscriptionFailedEvent;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PulsarControlPanelTest {

    @Mock BeanLocator beanLocator;

    @Test
    void buildsBodyFromRegisteredPulsarClients() {
        var producerRegistry = mock(PulsarProducerRegistry.class);
        var consumerRegistry = mock(PulsarConsumerRegistry.class);
        var readerRegistry = mock(PulsarReaderRegistry.class);
        var producer = mock(Producer.class);
        var consumer = mock(Consumer.class);
        var reader = mock(Reader.class);
        when(producerRegistry.getProducerIds()).thenReturn(Set.of("orders-producer"));
        when(producerRegistry.getProducer("orders-producer")).thenReturn(producer);
        when(producer.getProducerName()).thenReturn("orders");
        when(producer.getTopic()).thenReturn("persistent://public/default/orders");
        when(producer.isConnected()).thenReturn(true);
        when(producer.getNumOfPartitions()).thenReturn(3);
        when(consumerRegistry.getConsumerIds()).thenReturn(Set.of("orders-consumer"));
        when(consumerRegistry.getConsumer("orders-consumer")).thenReturn(consumer);
        when(consumerRegistry.isPaused("orders-consumer")).thenReturn(false);
        when(consumer.getConsumerName()).thenReturn("orders-worker");
        when(consumer.getTopic()).thenReturn("persistent://public/default/orders");
        when(consumer.getSubscription()).thenReturn("orders-sub");
        when(consumer.isConnected()).thenReturn(true);
        when(readerRegistry.getReaders()).thenReturn(List.of(reader));
        when(reader.getTopic()).thenReturn("persistent://public/default/audit");
        when(reader.isConnected()).thenReturn(false);
        when(reader.hasReachedEndOfTopic()).thenReturn(true);
        doReturn(Optional.of(producerRegistry)).when(beanLocator).findBean(Argument.of(PulsarProducerRegistry.class));
        doReturn(Optional.of(consumerRegistry)).when(beanLocator).findBean(Argument.of(PulsarConsumerRegistry.class));
        doReturn(Optional.of(readerRegistry)).when(beanLocator).findBean(Argument.of(PulsarReaderRegistry.class));

        var panel = panel(defaultConfiguration());

        var body = panel.getBody();

        assertEquals(1, body.summary().producers());
        assertEquals(1, body.summary().consumers());
        assertEquals(1, body.summary().readers());
        assertEquals(3, body.summary().totalClients());
        assertEquals("orders", body.producers().get(0).name());
        assertEquals("orders-sub", body.consumers().get(0).subscription());
        assertEquals("Disconnected", body.readers().get(0).state());
    }

    @Test
    void buildsDegradedEmptyBodyWhenRegistriesAreMissing() {
        doReturn(Optional.empty()).when(beanLocator).findBean(Argument.of(PulsarProducerRegistry.class));
        doReturn(Optional.empty()).when(beanLocator).findBean(Argument.of(PulsarConsumerRegistry.class));
        doReturn(Optional.empty()).when(beanLocator).findBean(Argument.of(PulsarReaderRegistry.class));

        var body = panel(defaultConfiguration()).getBody();

        assertEquals(0, body.summary().totalClients());
        assertFalse(body.producerRegistryPresent());
        assertFalse(body.consumerRegistryPresent());
        assertFalse(body.readerRegistryPresent());
    }

    @Test
    void applicationContextStartsWithoutRegisteredPulsarClients() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.environments", "test",
            "micronaut.control-panel.allowed-environments", "test"
        ))) {
            assertTrue(context.containsBean(PulsarControlPanel.class));
            var panel = context.getBean(PulsarControlPanel.class);
            var body = panel.getBody();
            assertEquals(0, body.summary().totalClients());
            assertTrue(body.consumerRegistryPresent());
        }
    }

    @Test
    void capturesBoundedFailureEventsWhenEnabled() {
        var configuration = new TestPulsarConfiguration(false, true, 1);
        var collector = new PulsarFailureEventCollector(configuration);

        collector.onApplicationEvent(new ConsumerSubscriptionFailedEvent(new IllegalStateException("first"), "orders"));
        collector.onApplicationEvent(new ConsumerSubscriptionFailedEvent(new IllegalArgumentException("second"), "invoices"));

        var failures = collector.snapshot();
        assertEquals(1, failures.size());
        assertEquals("Consumer", failures.get(0).clientType());
        assertEquals("invoices", failures.get(0).clientName());
        assertTrue(failures.get(0).reason().contains("invoices"));
        assertTrue(failures.get(0).error().contains("IllegalArgumentException"));
    }

    @Test
    void skipsFailureEventsWhenDisabled() {
        var configuration = new TestPulsarConfiguration(false, false, 25);
        var collector = new PulsarFailureEventCollector(configuration);

        collector.onApplicationEvent(new ConsumerSubscriptionFailedEvent(new IllegalStateException("failed"), "orders"));

        assertTrue(collector.snapshot().isEmpty());
    }

    private PulsarControlPanel panel(PulsarControlPanelConfiguration pulsarConfiguration) {
        var controlPanelConfiguration = new ControlPanelConfiguration(PulsarControlPanel.NAME);
        controlPanelConfiguration.setTitle("Pulsar");
        return new PulsarControlPanel(
            beanLocator,
            controlPanelConfiguration,
            pulsarConfiguration,
            new PulsarFailureEventCollector(pulsarConfiguration)
        );
    }

    private static PulsarControlPanelConfiguration defaultConfiguration() {
        return new TestPulsarConfiguration(false, true, 25);
    }

    private record TestPulsarConfiguration(boolean allowConsumerActions, boolean includeFailureEvents, int maxFailureEvents) implements PulsarControlPanelConfiguration {
        @Override
        public boolean isAllowConsumerActions() {
            return allowConsumerActions;
        }

        @Override
        public boolean isIncludeFailureEvents() {
            return includeFailureEvents;
        }

        @Override
        public int getMaxFailureEvents() {
            return maxFailureEvents;
        }
    }
}
