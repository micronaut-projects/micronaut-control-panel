/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.crac.OrderedResource;
import io.micronaut.crac.events.AfterRestoreEvent;
import io.micronaut.crac.events.BeforeCheckpointEvent;
import jakarta.inject.Singleton;
import org.crac.Context;
import org.crac.Resource;
import org.crac.management.CRaCMXBean;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CracControlPanelTest {

    @Test
    void panelLoadsAndReportsCustomResource() {
        try (ApplicationContext context = run(Map.of(CracControlPanel.ENABLED_PROPERTY, (Object) true))) {
            CracControlPanel panel = context.getBean(CracControlPanel.class);
            CracDiagnostics body = panel.getBody();

            assertEquals("CRaC", panel.getTitle());
            assertEquals("fa-arrows-rotate", panel.getIcon());
            assertTrue(body.support().supported());
            assertTrue(body.hasResources());
            assertTrue(body.resources().stream().anyMatch(resource ->
                resource.className().equals(CustomOrderedResource.class.getName())
                    && resource.category().equals("custom")
                    && !resource.readinessNote().isBlank()));
            assertFalse(body.events().stream().anyMatch(event -> event.className().contains("password")));
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = run(Map.of(CracControlPanel.ENABLED_PROPERTY, (Object) false))) {
            assertFalse(context.containsBean(CracControlPanel.class));
            assertFalse(context.containsBean(CracLifecycleEventHistory.class));
        }
    }

    @Test
    void panelFollowsGlobalControlPanelEnablement() {
        try (ApplicationContext context = run(Map.of(ControlPanelModuleConfiguration.PROPERTY_ENABLED, (Object) false))) {
            assertFalse(context.containsBean(CracControlPanel.class));
            assertFalse(context.containsBean(CracLifecycleEventHistory.class));
        }
    }

    @Test
    void recordsLifecycleEventsWithoutTriggeringCheckpoint() {
        try (ApplicationContext context = run()) {
            OrderedResource resource = context.getBeansOfType(OrderedResource.class)
                .stream()
                .filter(CustomOrderedResource.class::isInstance)
                .findFirst()
                .orElseThrow();

            context.publishEvent(new BeforeCheckpointEvent(resource, 3_000_000));
            context.publishEvent(new AfterRestoreEvent(resource, 4_000_000));

            CracDiagnostics body = context.getBean(CracControlPanel.class).getBody();
            assertEquals(2, body.events().size());
            assertTrue(body.events().stream().anyMatch(event ->
                event.phase().equals("beforeCheckpoint") && event.duration().equals("3 ms")));
            assertTrue(body.events().stream().anyMatch(event ->
                event.phase().equals("afterRestore") && event.duration().equals("4 ms")));
        }
    }

    @Test
    void lifecycleEventHistoryKeepsRecentEventsBounded() {
        try (ApplicationContext context = run()) {
            OrderedResource resource = context.getBeansOfType(OrderedResource.class)
                .stream()
                .filter(CustomOrderedResource.class::isInstance)
                .findFirst()
                .orElseThrow();

            for (int i = 0; i < 70; i++) {
                context.publishEvent(new BeforeCheckpointEvent(resource, i));
            }

            CracDiagnostics body = context.getBean(CracControlPanel.class).getBody();
            assertEquals(64, body.events().size());
            assertTrue(body.events().stream().allMatch(event -> event.phase().equals("beforeCheckpoint")));
            assertTrue(body.events().stream().allMatch(event -> event.beanName().equals("customOrderedResource")));
        }
    }

    @Test
    void supportDetectorHandlesMissingCracClasses() {
        CracSupportDetector detector = new CracSupportDetector(new ClassLoader(null) {
        }, () -> null);

        CracDiagnostics.Support support = detector.detect();
        CracDiagnostics.RestoreMetrics metrics = detector.restoreMetrics();

        assertFalse(support.supported());
        assertFalse(support.cracApiPresent());
        assertFalse(metrics.available());
        assertEquals("Unavailable", metrics.restoreTime());
    }

    @Test
    void supportDetectorReportsRestoreMetrics() {
        CRaCMXBean mxBean = mock(CRaCMXBean.class);
        when(mxBean.getRestoreTime()).thenReturn(1500L);
        when(mxBean.getUptimeSinceRestore()).thenReturn(61_000L);

        CracSupportDetector detector = new CracSupportDetector(CracSupportDetector.class.getClassLoader(), () -> mxBean);

        CracDiagnostics.RestoreMetrics metrics = detector.restoreMetrics();
        assertTrue(metrics.available());
        assertTrue(metrics.restored());
        assertEquals("1.500 s", metrics.restoreTime());
        assertEquals("1m 1s", metrics.uptimeSinceRestore());
    }

    @Test
    void supportDetectorReportsUnreadableRestoreMetrics() {
        CracSupportDetector detector = new CracSupportDetector(CracSupportDetector.class.getClassLoader(), () -> {
            throw new IllegalStateException("MXBean unavailable");
        });

        CracDiagnostics.RestoreMetrics metrics = detector.restoreMetrics();
        assertFalse(metrics.available());
        assertEquals("Unavailable", metrics.restoreTime());
        assertEquals("CRaC MXBean metrics are not readable on this JVM.", metrics.message());
    }

    @Test
    void formatsDurations() {
        assertEquals("Unavailable", CracSupportDetector.formatMillis(-1));
        assertEquals("999 ms", CracSupportDetector.formatMillis(999));
        assertEquals("1.001 s", CracSupportDetector.formatMillis(1001));
        assertEquals("2m 3s", CracSupportDetector.formatMillis(123_456));
        assertEquals("999 ns", CracSupportDetector.formatNanos(999));
        assertEquals("1 ms", CracSupportDetector.formatNanos(1_000_000));
    }

    @Test
    void diagnosticsHelpersReflectEmptyAndPopulatedState() {
        CracDiagnostics empty = new CracDiagnostics(
            new CracDiagnostics.Support(false, false, false, false, "missing"),
            new CracDiagnostics.Configuration(false, false, false, "Unavailable", "missing"),
            new CracDiagnostics.RedisConfiguration(false, false, false, false, false, "missing"),
            CracSupportDetector.unavailableRestoreMetrics("missing"),
            List.of(),
            List.of(),
            List.of()
        );
        assertFalse(empty.hasResources());
        assertFalse(empty.hasEvents());

        CracDiagnostics populated = new CracDiagnostics(
            empty.support(),
            empty.configuration(),
            empty.redis(),
            empty.restore(),
            List.of(new CracDiagnostics.Resource(1, "custom", "bean", "example.Resource", "Resource", "ready")),
            List.of(new CracDiagnostics.LifecycleEvent("phase", 1, "custom", "bean", "example.Resource", "Resource", "now", "1 ms")),
            List.of()
        );
        assertTrue(populated.hasResources());
        assertTrue(populated.hasEvents());
    }

    @Test
    void resourceClassifierProvidesReadinessNotes() {
        assertEquals("custom", CracResourceClassifier.category(new CustomOrderedResource()));
        assertEquals("CustomOrderedResource", CracResourceClassifier.simpleName(new CustomOrderedResource()));
        assertTrue(CracResourceClassifier.readinessNote("refresh").contains("Refresh scope"));
        assertTrue(CracResourceClassifier.readinessNote("netty").contains("Embedded server"));
        assertTrue(CracResourceClassifier.readinessNote("datasource").contains("Datasource"));
        assertTrue(CracResourceClassifier.readinessNote("redis").contains("Redis"));
        assertTrue(CracResourceClassifier.readinessNote("custom").contains("Custom ordered resource"));
        assertTrue(CracResourceClassifier.readinessNote("unknown").contains("unknown"));
    }

    @Test
    void templatesDoNotRenderResourceToStringValues() throws Exception {
        try (var detail = getClass().getResourceAsStream("/views/crac/detail.hbs")) {
            assertNotNull(detail);
            String template = new String(detail.readAllBytes(), StandardCharsets.UTF_8);
            String resourceSection = template.substring(template.indexOf("Resource chain"), template.indexOf("Recent lifecycle events"));
            assertFalse(resourceSection.contains("{{this}}"));
            assertFalse(template.contains("toString"));
        }
    }

    private static ApplicationContext run() {
        return run(Map.of());
    }

    private static ApplicationContext run(Map<String, Object> properties) {
        return ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments(Environment.TEST)
            .properties(properties)
            .start();
    }

    @Factory
    static class CustomResourceFactory {

        @Singleton
        CustomOrderedResource customResource() {
            return new CustomOrderedResource();
        }
    }

    static final class CustomOrderedResource implements OrderedResource {

        @Override
        public void beforeCheckpoint(Context<? extends Resource> context) {
            // Test fixture does not manage external CRaC resources.
        }

        @Override
        public void afterRestore(Context<? extends Resource> context) {
            // Test fixture does not manage external CRaC resources.
        }

        @Override
        public int getOrder() {
            return 42;
        }

        @Override
        public String toString() {
            return "password=secret";
        }
    }
}
