package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.crac.OrderedResource;
import io.micronaut.crac.test.CheckpointSimulator;
import jakarta.inject.Singleton;
import org.crac.Context;
import org.crac.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CracControlPanelTest {

    private static final String SPEC = "CracControlPanelTest";

    @Test
    void listsResourcesAndRecordsCheckpointAndRestoreEvents() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            CracControlPanel panel = context.getBean(CracControlPanel.class);
            assertEquals("CRaC", panel.getTitle());

            CracInfo before = panel.getBody();
            assertTrue(before.enabled());
            assertNull(before.restoreTime(), "the test JVM was not restored");
            assertTrue(before.events().isEmpty());
            CracInfo.Resource resource = before.resources().stream()
                .filter(r -> r.type().equals(CacheWarmer.class.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(before.resources().toString()));
            assertEquals(5, resource.order());

            CheckpointSimulator simulator = context.getBean(CheckpointSimulator.class);
            simulator.runBeforeCheckpoint();
            simulator.runAfterRestore();

            List<CracInfo.Event> events = panel.getBody().events();
            assertEquals("restore", events.getFirst().phase());
            // Micronaut CRaC publishes events for the resources it provides, such as the bean refresh resource
            assertTrue(events.stream().anyMatch(e -> e.phase().equals("checkpoint") && e.resource().equals("io.micronaut.crac.resources.RefreshEventResource")), events.toString());
            assertFalse(panel.getBody().toString().contains("cache-secret"), "resource state is not shown");

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(CracControlPanel.NAME).isPresent());
        }
    }

    @Test
    void reportsDisabledCrac() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("crac.enabled", false))) {
            CracInfo body = context.getBean(CracControlPanel.class).getBody();
            assertFalse(body.enabled());
            assertTrue(body.resources().isEmpty());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(CracControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(CracControlPanel.class));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class CacheWarmer implements OrderedResource {

        @Override
        public void beforeCheckpoint(Context<? extends Resource> context) {
        }

        @Override
        public void afterRestore(Context<? extends Resource> context) {
        }

        @Override
        public int getOrder() {
            return 5;
        }

        @Override
        public String toString() {
            return "CacheWarmer[cache-secret]";
        }
    }
}
