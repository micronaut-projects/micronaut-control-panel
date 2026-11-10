package io.micronaut.controlpanel.panels.rabbitmq;

import com.rabbitmq.client.Connection;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqControlPanelTest {

    private static final String SPEC = "RabbitMqControlPanelTest";

    @Test
    void listsConnectionsAndConsumers() {
        // rabbitmq.uri is provided by Micronaut Test Resources
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            RabbitMqControlPanel panel = context.getBean(RabbitMqControlPanel.class);
            assertEquals("RabbitMQ", panel.getTitle());
            assertEquals("", panel.getBadge());
            assertEquals("messaging", panel.getCategory().id());

            RabbitMqControlPanel.Body body = panel.getBody();
            RabbitMqConnectionInfo connection = body.connections().getFirst();
            assertEquals(RabbitMqInspector.DEFAULT_CONNECTION, connection.name());
            assertEquals(RabbitMqConnectionInfo.State.OPEN, connection.state(), body.toString());
            assertNotNull(connection.broker());
            assertTrue(connection.broker().startsWith("RabbitMQ"));
            assertTrue(body.allOpen());
            assertFalse(body.toString().contains("password"));

            List<RabbitMqConsumerInfo> consumers = body.consumers();
            assertEquals(List.of("audit", "orders"), consumers.stream().map(RabbitMqConsumerInfo::queue).toList());
            RabbitMqConsumerInfo orders = consumers.get(1);
            assertEquals(OrdersListener.class.getName(), orders.listener());
            assertEquals("receive", orders.method());
            assertEquals("default", orders.connection());
            assertEquals("3", orders.consumers());
            assertEquals(10, orders.prefetch());
            assertTrue(orders.reQueue());
            assertFalse(orders.autoAcknowledgment());

            RabbitMqConsumerInfo audit = consumers.getFirst();
            assertEquals("1", audit.consumers());
            assertEquals(0, audit.prefetch());
            assertTrue(audit.autoAcknowledgment());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(RabbitMqControlPanel.NAME).isPresent());
        }
    }

    @Test
    void stateBadgesAndLabels() {
        assertEquals("badge-secondary", connection(RabbitMqConnectionInfo.State.OPEN).badgeClass());
        assertEquals("open", connection(RabbitMqConnectionInfo.State.OPEN).stateLabel());
        assertEquals("badge-destructive", connection(RabbitMqConnectionInfo.State.CLOSED).badgeClass());
        assertEquals("closed", connection(RabbitMqConnectionInfo.State.CLOSED).stateLabel());
        assertEquals("badge-secondary", connection(RabbitMqConnectionInfo.State.NOT_OPENED).badgeClass());
        assertEquals("not opened yet", connection(RabbitMqConnectionInfo.State.NOT_OPENED).stateLabel());

        assertTrue(new RabbitMqControlPanel.Body(List.of(connection(RabbitMqConnectionInfo.State.NOT_OPENED)), List.of()).allOpen());
        assertFalse(new RabbitMqControlPanel.Body(List.of(connection(RabbitMqConnectionInfo.State.CLOSED)), List.of()).allOpen());
    }

    @Test
    void consumerCountsAreLabelled() {
        assertEquals("1 consumer", consumer("1").consumersLabel());
        assertEquals("3 consumers", consumer("3").consumersLabel());
    }

    @Test
    void brokerIsDescribedFromTheServerProperties() {
        assertEquals("RabbitMQ 4.1.0", RabbitMqInspector.broker(connectionWith(Map.of("product", "RabbitMQ", "version", "4.1.0"))));
        assertEquals("RabbitMQ", RabbitMqInspector.broker(connectionWith(Map.of("product", "RabbitMQ"))));
        assertNull(RabbitMqInspector.broker(connectionWith(Map.of())));
        assertNull(RabbitMqInspector.broker(connectionWith(null)));
    }

    private static RabbitMqConnectionInfo connection(RabbitMqConnectionInfo.State state) {
        return new RabbitMqConnectionInfo("default", List.of("localhost:5672"), "/", "guest", false, null, null, state, null);
    }

    private static RabbitMqConsumerInfo consumer(String consumers) {
        return new RabbitMqConsumerInfo("q", "L", "m", "default", null, consumers, 0, false, false, false);
    }

    private static Connection connectionWith(Map<String, Object> serverProperties) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
            (proxy, method, args) -> "getServerProperties".equals(method.getName()) ? serverProperties : null);
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(RabbitMqControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(RabbitMqControlPanel.class));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @RabbitListener
    static class OrdersListener {

        @Queue(value = "orders", numberOfConsumers = "3", prefetch = 10, reQueue = true)
        void receive(String order) {
        }

        @Queue(value = "audit", autoAcknowledgment = true)
        void audit(String event) {
        }
    }
}
