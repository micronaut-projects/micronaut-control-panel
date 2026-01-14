package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of("endpoints.env.enabled", true))) {
            EnvironmentControlPanel panel = ctx.getBean(EnvironmentControlPanel.class);
            assertEquals("Environment Properties", panel.getTitle());
            assertEquals("fa-sliders", panel.getIcon());
            assertEquals(10, panel.getOrder());
            @SuppressWarnings("unchecked")
            var body = (java.util.Map<String, Object>) panel.getBody();
            @SuppressWarnings("unchecked")
            var activeEnvironments = (java.util.Set<String>) body.get("activeEnvironments");
            assertEquals(java.util.Set.of(Environment.TEST), activeEnvironments);
            @SuppressWarnings("unchecked")
            var packages = (java.util.Collection<String>) body.get("packages");
            assertNotNull(packages);
            assertFalse(packages.isEmpty());
            @SuppressWarnings("unchecked")
            var propertySources = (java.util.Collection<Object>) body.get("propertySources");
            assertEquals(4, propertySources.size());
        }
    }
}
