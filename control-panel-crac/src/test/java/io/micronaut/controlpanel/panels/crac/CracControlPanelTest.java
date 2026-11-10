package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.crac.OrderedResource;
import io.micronaut.crac.events.AfterRestoreEvent;
import io.micronaut.crac.events.BeforeCheckpointEvent;
import io.micronaut.crac.resources.redis.CracRedisConfiguration;
import io.micronaut.crac.test.CheckpointSimulator;
import jakarta.inject.Singleton;
import org.crac.Context;
import org.crac.Resource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    @Test
    void durationsAreFormatted() {
        assertEquals("500µs", CracInspector.format(Duration.ofNanos(500_000)));
        assertEquals("250ms", CracInspector.format(Duration.ofMillis(250)));
        assertEquals("5s", CracInspector.format(Duration.ofSeconds(5)));
        assertEquals("1.5s", CracInspector.format(Duration.ofMillis(1500)));
        assertEquals("2m 30s", CracInspector.format(Duration.ofSeconds(150)));
    }

    @Test
    void onlyTheLatestEventsAreKept() {
        CracEventRecorder recorder = new CracEventRecorder();
        CacheWarmer resource = new CacheWarmer();
        for (int i = 0; i < CracEventRecorder.MAX_EVENTS; i++) {
            recorder.onBeforeCheckpoint(new BeforeCheckpointEvent(resource, 1_000));
        }
        recorder.onAfterRestore(new AfterRestoreEvent(resource, 2_000_000));

        List<CracInfo.Event> events = recorder.events();
        assertEquals(CracEventRecorder.MAX_EVENTS, events.size());
        assertEquals("restore", events.getFirst().phase());
        assertEquals("2ms", events.getFirst().duration());
        assertEquals(CacheWarmer.class.getName(), events.getFirst().resource());
    }

    @Test
    void redisResourcesAreDescribed() {
        assertEquals(List.of("connections", "clients", "caches"), CracInspector.redis(redis(true, true, true, true)));
        assertEquals(List.of(), CracInspector.redis(redis(true, false, false, false)));
        assertEquals(List.of(), CracInspector.redis(redis(false, true, true, true)));
    }

    @Test
    void hasNoBadge() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            assertEquals("", context.getBean(CracControlPanel.class).getBadge());
        }
    }

    private static CracRedisConfiguration redis(boolean enabled, boolean connection, boolean client, boolean cache) {
        return new CracRedisConfiguration() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public boolean isCacheEnabled() {
                return cache;
            }

            @Override
            public boolean isClientEnabled() {
                return client;
            }

            @Override
            public boolean isConnectionEnabled() {
                return connection;
            }
        };
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
