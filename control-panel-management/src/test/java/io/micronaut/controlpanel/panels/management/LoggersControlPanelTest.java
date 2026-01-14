package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.logging.LogLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggersControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of("endpoints.loggers.enabled", true))) {
            LoggersControlPanel panel = ctx.getBean(LoggersControlPanel.class);
            assertEquals("Loggers", panel.getTitle());
            assertEquals("fa-file-lines", panel.getIcon());
            assertEquals(40, panel.getOrder());
            assertEquals(LogLevel.values().length, panel.getBody().levels().size());
            assertEquals(2, panel.getBody().loggers().keySet().size());
            assertEquals(LogLevel.INFO, panel.getBody().loggers().get("ROOT").get("configuredLevel"));
            assertEquals(LogLevel.DEBUG, panel.getBody().loggers().get("io.micronaut.controlpanel").get("configuredLevel"));
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(LoggersControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(LoggersControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(LoggersControlPanel.class));
        }
    }
}
