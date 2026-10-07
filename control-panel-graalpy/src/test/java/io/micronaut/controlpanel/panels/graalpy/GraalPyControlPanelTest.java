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
package io.micronaut.controlpanel.panels.graalpy;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.python.PythonPoolStatistics;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalPyControlPanelTest {

    private static final String SPEC_NAME = "GraalPyControlPanelTest";

    @Test
    void listsGeneratedPythonBeansWithoutCreatingThem() {
        try (ApplicationContext context = start()) {
            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertTrue(body.beanMetadataAvailable());
            assertEquals(2, body.pythonBeanCount());

            PythonBeanScanner.PythonBean module = bean(body, TestPythonBeans.DealerModule.class);
            assertEquals("module", module.kind());
            assertEquals("dealer", module.pythonIdentity());
            assertEquals("cards", module.packageName());
            assertEquals("Singleton", module.scope());
            assertEquals("Not created", module.state());
            assertTrue(module.methods().stream().anyMatch(method -> method.signature().equals("String deal(String name, int count)")));

            PythonBeanScanner.PythonBean pythonClass = bean(body, TestPythonBeans.PricingRules.class);
            assertEquals("class", pythonClass.kind());
            assertEquals("Pricing rules", pythonClass.pythonIdentity());
            assertEquals("pricing", pythonClass.packageName());
            assertEquals(List.of("quote"), pythonClass.nestedMembers());
            assertEquals("Prototype (@ContextPooled)", pythonClass.scope());
            assertEquals("Per pooled context", pythonClass.state());
            assertTrue(pythonClass.methods().stream().anyMatch(method -> method.name().equals("quote")));
        }
    }

    @Test
    void badgeCountsPythonBeans() {
        try (ApplicationContext context = start()) {
            assertEquals("2", context.getBean(GraalPyControlPanel.class).getBadge());
        }
    }

    @Test
    void rendersThePoolSnapshotWithoutBorrowingAContext() {
        try (ApplicationContext context = start()) {
            TestPythonContextExecutor executor = context.getBean(TestPythonContextExecutor.class);
            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertTrue(body.pool().available());
            assertEquals("Pooled 3/4", body.pool().state());
            assertEquals(2, body.pool().statistics().idleContexts());
            assertEquals(10, body.pool().statistics().borrows());
            assertTrue(body.pool().hasHints());
            assertEquals(0, executor.withContextCalls());
        }
    }

    @Test
    void reportsDisabledPooling() {
        try (ApplicationContext context = start()) {
            context.getBean(TestPythonContextExecutor.class)
                .statistics(new PythonPoolStatistics(false, 0, 0, 0, 0, 0, 0, 0, 0, false));

            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertEquals("Disabled", body.pool().state());
            assertTrue(body.pool().hasMessage());
            assertFalse(body.pool().hasHints());
        }
    }

    @Test
    void reportsAClosedPool() {
        try (ApplicationContext context = start()) {
            context.getBean(TestPythonContextExecutor.class)
                .statistics(new PythonPoolStatistics(true, 4, 0, 0, 0, 12, 0, 0, 0, true));

            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertEquals("Closed", body.pool().state());
            assertTrue(body.pool().hasMessage());
        }
    }

    @Test
    void reportsUnavailableStatisticsWithoutFailingThePage() {
        try (ApplicationContext context = start()) {
            context.getBean(TestPythonContextExecutor.class).failWith(new IllegalStateException("pool gone"));

            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertEquals("Unavailable", body.pool().state());
            assertFalse(body.pool().available());
            assertNotNull(body.pool().settings());
            assertTrue(body.pool().hasMessage());
        }
    }

    @Test
    void separatesNotConfiguredFromUnavailableWhenCorePythonBeansAreDisabled() {
        try (ApplicationContext context = start()) {
            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertFalse(body.configuration().available());
            assertFalse(body.configuration().hasOptions());
            assertFalse(body.configuration().hasHostClassLookup());
            assertEquals("Not configured", body.pool().settings().poolEnabled());
            assertEquals("Not configured", body.pool().settings().pythonEnabled());
            assertFalse(body.pool().engine().available());
            assertEquals("Unknown", body.pool().engine().version());
        }
    }

    @Test
    void listsContextCustomizersWithoutInstantiatingThem() {
        try (ApplicationContext context = start()) {
            int instantiationsBefore = RecordingContextCustomizer.instantiations();
            int customizationsBefore = RecordingContextCustomizer.customizations();

            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertTrue(body.configuration().customizers().contains(RecordingContextCustomizer.class.getName()));
            assertEquals(instantiationsBefore, RecordingContextCustomizer.instantiations());
            assertEquals(customizationsBefore, RecordingContextCustomizer.customizations());
        }
    }

    @Test
    void showsAnEmptyStateWhenNoPythonBeansArePresent() {
        try (ApplicationContext context = start("GraalPyControlPanelEmptyTest", Map.of())) {
            GraalPyControlPanel.Body body = context.getBean(GraalPyControlPanel.class).getBody();

            assertFalse(body.hasPythonBeans());
            assertEquals(0, body.pythonBeanCount());
            assertTrue(body.beanMetadataAvailable());
            // micronaut-context-python packages its own runtime sources under the core application VFS root,
            // so the VFS section is populated even before an application adds Python sources.
            assertTrue(body.vfs().hasResources());
            assertEquals(GraalPyVfsMetadataReader.APPLICATION_ROOT, body.vfs().resources().get(0).root());
        }
    }

    @Test
    void panelIsAbsentWithoutAPythonContextExecutorBean() {
        try (ApplicationContext context = start("GraalPyControlPanelDisabledTest", Map.of())) {
            assertTrue(context.findBean(GraalPyControlPanel.class).isEmpty());
        }
    }

    @Test
    void panelIsAbsentWhenExplicitlyDisabled() {
        try (ApplicationContext context = start("GraalPyControlPanelEmptyTest",
            Map.of(GraalPyControlPanel.ENABLED_PROPERTY, false))) {
            assertTrue(context.getBeanDefinitions(GraalPyControlPanel.class).isEmpty());
            assertTrue(context.findBean(GraalPyControlPanel.class).isEmpty());
            // The off-switch is the package-level @Requires in package-info.java, so it removes the whole panel
            // package rather than only the panel bean: no scanner and no VFS reader are left to inspect anything.
            assertTrue(context.getBeanDefinitions(PythonBeanScanner.class).isEmpty());
            assertTrue(context.getBeanDefinitions(GraalPyVfsMetadataReader.class).isEmpty());
        }
    }

    @Test
    void panelIsPresentWhenTheEnabledPropertyIsNotOverridden() {
        try (ApplicationContext context = start("GraalPyControlPanelEmptyTest", Map.of())) {
            assertEquals(Boolean.TRUE, context.getProperty(GraalPyControlPanel.ENABLED_PROPERTY, Boolean.class).orElse(null));
            assertTrue(context.findBean(GraalPyControlPanel.class).isPresent());
        }
    }

    @Test
    void detectsOptionalRuntimeModules() {
        try (ApplicationContext context = start()) {
            PythonRuntimeInspector.RuntimeModules modules = context.getBean(GraalPyControlPanel.class).getBody().modules();

            assertFalse(modules.nettyPresent());
            assertFalse(modules.poolEndpointPresent());
        }
    }

    private static PythonBeanScanner.PythonBean bean(GraalPyControlPanel.Body body, Class<?> beanType) {
        return body.pythonBeans()
            .stream()
            .filter(bean -> bean.javaType().equals(beanType.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No Python bean found for " + beanType.getName()));
    }

    private static ApplicationContext start() {
        return start(SPEC_NAME, Map.of());
    }

    /**
     * Starts a context with Core's Python support switched off so no GraalPy engine or context is ever built.
     *
     * @param specName the spec name scoping the test fixtures
     * @param properties extra properties
     * @return the started context
     */
    private static ApplicationContext start(String specName, Map<String, Object> properties) {
        Map<String, Object> allProperties = new LinkedHashMap<>(properties);
        allProperties.put("spec.name", specName);
        allProperties.put("micronaut.python.enabled", false);
        return ApplicationContext.run(allProperties);
    }
}
