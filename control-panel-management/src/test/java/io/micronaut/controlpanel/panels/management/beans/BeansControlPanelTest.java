package io.micronaut.controlpanel.panels.management.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeansControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            BeansControlPanel panel = ctx.getBean(BeansControlPanel.class);
            assertEquals("Bean Definitions", panel.getTitle());
            assertEquals("fa-plug", panel.getIcon());
            assertEquals(0, panel.getOrder());
            assertNotNull(panel.getBody().micronautBeansByPackage());
            assertNotNull(panel.getBody().otherBeansByPackage());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(BeansControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(BeansControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(BeansControlPanel.class));
        }
    }

    @Test
    void itHandlesPrimitiveTypeBeansCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ctx.registerSingleton(int.class, 42);
            BeansControlPanel panel = ctx.getBean(BeansControlPanel.class);
            assertNotNull(panel);
            assertNotNull(panel.getBody().micronautBeansByPackage());
            assertNotNull(panel.getBody().otherBeansByPackage());
            assertTrue(panel.getBody().otherBeansByPackage().containsKey("primitive"));
        }
    }
}
