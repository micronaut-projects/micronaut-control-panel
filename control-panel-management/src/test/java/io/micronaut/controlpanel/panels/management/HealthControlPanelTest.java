package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.health.HealthStatus;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(ServiceReadyHealthIndicator.ENABLED, false))) {
            HealthControlPanel panel = ctx.getBean(HealthControlPanel.class);
            assertEquals("Application Health", panel.getTitle());
            assertEquals("fa-laptop-medical", panel.getIcon());
            assertEquals(0, panel.getOrder());
            assertEquals(HealthStatus.UP, panel.getBody().getStatus());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(HealthControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(HealthControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(HealthControlPanel.class));
        }
    }
}
