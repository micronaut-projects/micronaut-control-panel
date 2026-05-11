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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void templatesDoNotRenderResourceToStringValues() throws Exception {
        try (var detail = getClass().getResourceAsStream("/views/crac/detail.hbs")) {
            assertNotNull(detail);
            String template = new String(detail.readAllBytes());
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
        }

        @Override
        public void afterRestore(Context<? extends Resource> context) {
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
