package example;

import example.pulsar.PulsarExampleConsumer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MyApplicationControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("infinispan.enabled",  "false"))) {
            // When: retrieve the panel and its configuration
            MyApplicationControlPanel panel = ctx.getBean(MyApplicationControlPanel.class);
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(panel.getName()));

            // Then: configuration is enabled
            assertTrue(cfg.isEnabled(), "Control panel should be enabled");

            // And: panel properties match configuration/application.yml
            assertEquals("My Application Control Panel", panel.getTitle());
            assertEquals("fa-plug", panel.getIcon());
            assertEquals(10, panel.getOrder());

            // And: body content matches implementation
            MyApplicationControlPanel.Body body = panel.getBody();
            assertEquals("This is an application-provided control panel. This text is coming from the body.", body.text());
        }
    }

    @Test
    void pulsarExampleConsumerIsConfiguredWhenPulsarIsAvailable() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "infinispan.enabled", "false",
            "pulsar.service-url", "pulsar://localhost:6650"
        ))) {
            assertTrue(ctx.containsBean(PulsarExampleConsumer.class), "Pulsar example consumer should be a message listener bean");
        }
    }
}
