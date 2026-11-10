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
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.pulsar.annotation.PulsarConsumer;
import io.micronaut.pulsar.annotation.PulsarProducer;
import io.micronaut.pulsar.annotation.PulsarProducerClient;
import io.micronaut.pulsar.annotation.PulsarSubscription;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import io.micronaut.pulsar.PulsarProducerRegistry;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerStats;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Reader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PulsarControlPanelTest {

    private static final String SPEC = "PulsarControlPanelTest";
    private static final String TOPIC = "persistent://public/default/orders";

    @Test
    void listsClientConsumersAndProducers() {
        // The Pulsar broker is started by Micronaut Test Resources, see application-test.yml
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            PulsarControlPanel panel = context.getBean(PulsarControlPanel.class);
            assertEquals("Pulsar", panel.getTitle());
            assertEquals("", panel.getBadge());
            assertEquals("messaging", panel.getCategory().id());

            PulsarControlPanel.Body body = panel.getBody();
            assertNotNull(body.client());
            assertTrue(body.client().serviceUrl().startsWith("pulsar://"), body.client().serviceUrl());
            assertEquals("none", body.client().authentication());
            assertFalse(body.client().tls());
            assertTrue(body.producers().isEmpty(), "producers are created the first time they are used");

            context.getBean(OrderProducer.class).send("order-1");

            body = panel.getBody();
            List<PulsarConsumerInfo> consumers = body.consumers();
            assertEquals(1, consumers.size(), body.toString());
            PulsarConsumerInfo consumer = consumers.getFirst();
            assertEquals(TOPIC, consumer.topic());
            assertEquals("orders-subscription", consumer.subscription());
            assertEquals("orders-consumer", consumer.consumerName());
            assertTrue(consumer.connected());
            assertEquals("connected", consumer.statusLabel());
            assertEquals(0, body.disconnectedConsumers());

            PulsarProducerInfo producer = body.producers().getFirst();
            assertEquals(TOPIC, producer.topic());
            assertEquals("orders-producer", producer.producerName());
            assertTrue(producer.connected());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(PulsarControlPanel.NAME).isPresent());
        }
    }

    @Test
    void urlsAreShownWithoutCredentials() {
        assertEquals("pulsar://broker:6650", PulsarInspector.sanitize("pulsar://user:s3cret@broker:6650"));
        assertEquals("pulsar+ssl://b1:6651,b2:6651", PulsarInspector.sanitize("pulsar+ssl://token@b1:6651,b2:6651"));
        assertEquals("https://proxy:443/admin", PulsarInspector.sanitize("https://proxy:443/admin?token=abc#x"));
        assertEquals("(hidden)", PulsarInspector.sanitize("user:pass@broker"));
        assertEquals("broker:6650", PulsarInspector.sanitize("broker:6650"));
        assertEquals("pulsar://broker:6650", PulsarInspector.sanitize("pulsar://broker:6650#x"));
    }

    @Test
    void authenticationIsShownByMethodOnly() {
        assertEquals("token", PulsarInspector.authenticationLabel("AuthenticationToken"));
        assertEquals("none", PulsarInspector.authenticationLabel("AuthenticationDisabled"));
        assertEquals("CustomAuth", PulsarInspector.authenticationLabel("CustomAuth"));
        assertEquals("OAuth 2.0", PulsarInspector.authenticationLabel("AuthenticationOAuth2"));
        assertEquals("TLS client certificate", PulsarInspector.authenticationLabel("AuthenticationTls"));
        assertEquals("TLS client certificate", PulsarInspector.authenticationLabel("AuthenticationKeyStoreTls"));
        assertEquals("basic", PulsarInspector.authenticationLabel("AuthenticationBasic"));
        assertEquals("SASL", PulsarInspector.authenticationLabel("AuthenticationSasl"));
        assertEquals("Athenz", PulsarInspector.authenticationLabel("AuthenticationAthenz"));
    }

    @Test
    void consumersAreReadFromTheRegistry() {
        Consumer<?> disconnected = proxy(Consumer.class, Map.of("getTopic", "t2", "getSubscription", "s", "getConsumerName", "c2", "isConnected", false));
        Consumer<?> paused = proxy(Consumer.class, Map.of("getTopic", "t1", "getSubscription", "s", "getConsumerName", "c1", "isConnected", true,
            "getStats", proxy(ConsumerStats.class, Map.of("getTotalMsgsReceived", 5L, "getTotalAcksSent", 4L, "getTotalReceivedFailed", 1L))));
        Consumer<?> broken = proxy(Consumer.class, Map.of("getTopic", new IllegalStateException("closed")));
        PulsarConsumerRegistry registry = proxy(PulsarConsumerRegistry.class, Map.of(
            "getConsumers", Map.of("a", disconnected, "b", paused, "c", broken),
            "isPaused", new IllegalStateException("unknown")));

        List<PulsarConsumerInfo> consumers = PulsarInspector.consumers(registry);
        assertEquals(List.of("t1", "t2"), consumers.stream().map(PulsarConsumerInfo::topic).toList());
        assertEquals(5, consumers.getFirst().received());
        assertEquals(1, consumers.getFirst().failed());
        assertEquals(0, consumers.get(1).received());
        assertEquals("disconnected", consumers.get(1).statusLabel());
        assertEquals("badge-destructive", consumers.get(1).badgeClass());
        assertEquals(1, new PulsarControlPanel.Body(null, consumers, List.of(), List.of()).disconnectedConsumers());

        PulsarConsumerInfo pausedInfo = new PulsarConsumerInfo("b", "t1", "s", "c1", true, true, 0, 0, 0);
        assertEquals("paused", pausedInfo.statusLabel());
        assertEquals("badge-secondary", pausedInfo.badgeClass());
    }

    @Test
    void producersAndReadersAreReadFromTheRegistries() {
        Producer<?> disconnected = proxy(Producer.class, Map.of("getTopic", "t1", "getProducerName", "p1", "isConnected", false));
        Producer<?> broken = proxy(Producer.class, Map.of("getTopic", new IllegalStateException("closed")));
        PulsarProducerRegistry registry = proxy(PulsarProducerRegistry.class, Map.of("getProducers", Map.of("a", disconnected, "b", broken)));

        List<PulsarProducerInfo> producers = PulsarInspector.producers(registry);
        assertEquals(1, producers.size());
        assertEquals(0, producers.getFirst().sent());
        assertEquals("badge-destructive", producers.getFirst().badgeClass());

        PulsarReaderInfo reader = PulsarInspector.reader(proxy(Reader.class, Map.of("getTopic", "t1", "isConnected", true)));
        assertEquals("t1", reader.topic());
        assertTrue(reader.connected());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object answer = answers.get(method.getName());
            if (answer instanceof RuntimeException e) {
                throw e;
            }
            return answer;
        });
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(PulsarControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(PulsarControlPanel.class));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @PulsarSubscription(subscriptionName = "orders-subscription")
    static class OrderListener {

        @PulsarConsumer(topic = TOPIC, consumerName = "orders-consumer")
        void receive(String order) {
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @PulsarProducerClient
    interface OrderProducer {

        @PulsarProducer(topic = TOPIC, producerName = "orders-producer")
        void send(String order);
    }
}
