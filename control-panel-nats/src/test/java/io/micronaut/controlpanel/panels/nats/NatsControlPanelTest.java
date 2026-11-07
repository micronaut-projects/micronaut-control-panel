package io.micronaut.controlpanel.panels.nats;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.nats.annotation.NatsListener;
import io.micronaut.nats.annotation.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatsControlPanelTest {

    private static final String SPEC = "NatsControlPanelTest";

    @Test
    void listsConnectionsAndSubscriptions() {
        // The NATS server is started by Micronaut Test Resources, see application-test.yml
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            NatsControlPanel panel = context.getBean(NatsControlPanel.class);
            assertEquals("NATS", panel.getTitle());
            assertEquals("messaging", panel.getCategory().id());

            NatsControlPanel.Body body = panel.getBody();
            NatsConnectionInfo connection = body.connections().getFirst();
            assertEquals(NatsInspector.DEFAULT_CONNECTION, connection.name());
            assertEquals("CONNECTED", connection.status(), body.toString());
            assertNotNull(connection.server());
            assertNotNull(connection.statistics());
            assertEquals("none", connection.authentication());

            List<NatsSubscriptionInfo> subscriptions = body.subscriptions();
            assertEquals(List.of("orders.>", "payments.settled"), subscriptions.stream().map(NatsSubscriptionInfo::subject).toList());
            assertEquals("order-workers", subscriptions.getFirst().queue());
            assertNull(subscriptions.get(1).queue());
            assertEquals("default", subscriptions.get(1).connection());
            assertEquals("settled", subscriptions.get(1).method());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(NatsControlPanel.NAME).isPresent());
        }
    }

    @Test
    void addressesAreShownWithoutCredentials() {
        assertEquals("nats://broker:4222", NatsInspector.sanitize("nats://user:s3cret@broker:4222"));
        assertEquals("tls://broker:4443", NatsInspector.sanitize("tls://token@broker:4443?x=y"));
        assertEquals("(hidden)", NatsInspector.sanitize("not a uri with spaces"));
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(NatsControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(NatsControlPanel.class));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @NatsListener
    static class Listeners {

        @Subject(value = "orders.>", queue = "order-workers")
        void orders(String order) {
        }

        @Subject("payments.settled")
        void settled(String payment) {
        }
    }
}
