package io.micronaut.controlpanel.panels.nats;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.nats.annotation.NatsListener;
import io.micronaut.nats.annotation.Subject;
import io.micronaut.nats.connect.NatsConnectionFactoryConfig;
import io.nats.client.api.ServerInfo;
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
            assertEquals("", panel.getBadge());
            assertEquals("messaging", panel.getCategory().id());

            NatsControlPanel.Body body = panel.getBody();
            NatsConnectionInfo connection = body.connections().getFirst();
            assertEquals(NatsInspector.DEFAULT_CONNECTION, connection.name());
            assertEquals("CONNECTED", connection.status(), body.toString());
            assertNotNull(connection.server());
            assertNotNull(connection.statistics());
            assertEquals("none", connection.authentication());
            assertEquals("badge-secondary", connection.badgeClass());
            assertEquals("connected", connection.statusLabel());

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
    void addressesWithoutHostAreHiddenWhenTheyCarryCredentials() {
        assertEquals("localhost:4222", NatsInspector.sanitize("localhost:4222"));
        assertEquals("(hidden)", NatsInspector.sanitize("user:secret@localhost:4222"));
    }

    @Test
    void describesTheAuthenticationWithoutSecrets() {
        NatsConnectionFactoryConfig config = new NatsConnectionFactoryConfig("auth");
        assertEquals("none", NatsInspector.authentication(config));
        config.setAddresses(List.of("nats://alice:secret@broker:4222"));
        assertEquals("user in address", NatsInspector.authentication(config));
        config.setUsername("bob");
        config.setPassword("secret");
        assertEquals("user bob", NatsInspector.authentication(config));
        config.setToken("token-secret");
        assertEquals("token", NatsInspector.authentication(config));
        config.setCredentials("/secret/nats.creds");
        assertEquals("credentials file", NatsInspector.authentication(config));
    }

    @Test
    void describesTheServer() {
        assertNull(NatsInspector.server(null));
        assertEquals("n1 2.11.0", NatsInspector.server(new ServerInfo("INFO {\"server_name\":\"n1\",\"version\":\"2.11.0\"}")));
        assertEquals("n1 2.11.0, cluster c1",
            NatsInspector.server(new ServerInfo("INFO {\"server_name\":\"n1\",\"version\":\"2.11.0\",\"cluster\":\"c1\"}")));
    }

    @Test
    void statusBadgeReflectsTheConnectionState() {
        NatsConnectionInfo notOpened = new NatsConnectionInfo("a", List.of(), "none", false, null, null, null, null);
        assertEquals("badge-secondary", notOpened.badgeClass());
        assertEquals("not opened yet", notOpened.statusLabel());
        NatsConnectionInfo reconnecting = new NatsConnectionInfo("a", List.of(), "none", false, "RECONNECTING", null, null, null);
        assertEquals("badge-destructive", reconnecting.badgeClass());
        assertEquals("reconnecting", reconnecting.statusLabel());
        NatsConnectionInfo disconnected = new NatsConnectionInfo("a", List.of(), "none", false, "DISCONNECTED", null, null, null);
        assertEquals("badge-destructive", disconnected.badgeClass());
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
