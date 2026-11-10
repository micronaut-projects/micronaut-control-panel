package io.micronaut.controlpanel.panels.httpclient;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientsControlPanelTest {

    @Test
    void panelIsRegistered() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.http.services.orders.url", "http://orders.example.com"
        ))) {
            HttpClientsControlPanel panel = context.getBean(HttpClientsControlPanel.class);
            assertEquals("HTTP Clients", panel.getTitle());
            assertEquals("", panel.getBadge());
            HttpClientsControlPanel.Body body = panel.getBody();
            assertEquals(1, body.clients().size());
            assertEquals(0, body.declarativeCount());
            assertEquals(0, body.unresolvedCount());
            assertTrue(context.getBean(ControlPanelRepository.class).findByName(HttpClientsControlPanel.NAME).isPresent());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            HttpClientsControlPanel.ENABLED_PROPERTY, "false"
        ))) {
            assertFalse(context.containsBean(HttpClientsControlPanel.class));
        }
    }
}
