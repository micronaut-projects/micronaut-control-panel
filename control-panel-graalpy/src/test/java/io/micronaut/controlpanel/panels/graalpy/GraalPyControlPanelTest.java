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

import io.micronaut.context.BeanContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.graal.graalpy.GraalPyContextBuilderFactory;
import io.micronaut.graal.graalpy.annotations.GraalPyModule;
import io.micronaut.inject.BeanDefinition;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class GraalPyControlPanelTest {

    @Test
    void discoversGraalPyModuleDefinitionsWithoutInstantiatingPythonModules() {
        BeanContext beanContext = mock(BeanContext.class);
        ControlPanelConfiguration configuration = configuration();
        BeanDefinition<?> moduleDefinition = moduleDefinition("dealerService", DealerService.class, "dealer");
        BeanDefinition<?> factoryDefinition = beanDefinition(ExplodingFactory.class);

        doReturn(List.of(moduleDefinition)).when(beanContext).getAllBeanDefinitions();
        doReturn(List.of(factoryDefinition)).when(beanContext).getBeanDefinitions(GraalPyContextBuilderFactory.class);

        GraalPyControlPanel panel = new GraalPyControlPanel(beanContext, new GraalPyVfsMetadataReader(), configuration);
        GraalPyControlPanel.Body body = getBodyWithEmptyVfs(panel);

        assertEquals(1, body.moduleCount());
        GraalPyControlPanel.ModuleInterface module = body.modules().get(0);
        assertEquals("dealerService", module.beanName());
        assertEquals(DealerService.class.getName(), module.javaType());
        assertEquals("dealer", module.pythonModule());
        assertEquals("Unknown", module.instantiated());
        assertTrue(module.methods().stream().anyMatch(method -> method.signature().startsWith("String deal(String, int)")));
        assertTrue(module.methods().stream()
            .anyMatch(method -> method.signature().startsWith("java.util.concurrent.CompletableFuture<String> dealAsync()")));
        assertEquals("Available", body.runtime().factoryStatus());
        assertEquals(ExplodingFactory.class.getName(), body.runtime().activeFactoryClass());
        assertEquals(0, ExplodingFactory.createBuilderCalls.get());
    }

    @Test
    void reportsEmptyModulesWhenNoAnnotatedDefinitionsExist() {
        BeanContext beanContext = mock(BeanContext.class);
        doReturn(List.of(beanDefinition(String.class))).when(beanContext).getAllBeanDefinitions();
        doReturn(List.of()).when(beanContext).getBeanDefinitions(GraalPyContextBuilderFactory.class);

        GraalPyControlPanel.Body body = getBodyWithEmptyVfs(
            new GraalPyControlPanel(beanContext, new GraalPyVfsMetadataReader(), configuration()));

        assertFalse(body.hasModules());
        assertEquals(0, body.moduleCount());
        assertEquals("Unavailable", body.runtime().factoryStatus());
    }

    private static GraalPyControlPanel.Body getBodyWithEmptyVfs(GraalPyControlPanel panel) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            });
            return panel.getBody();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ControlPanelConfiguration configuration() {
        ControlPanelConfiguration configuration = new ControlPanelConfiguration(GraalPyControlPanel.NAME);
        configuration.setTitle("GraalPy");
        configuration.setIcon("fa-brands fa-python");
        return configuration;
    }

    private static BeanDefinition<?> moduleDefinition(String beanName, Class<?> beanType, String moduleName) {
        BeanDefinition<?> definition = beanDefinition(beanType);
        when(definition.getName()).thenReturn(beanName);
        when(definition.hasStereotype(GraalPyModule.class)).thenReturn(true);
        when(definition.hasDeclaredStereotype(GraalPyModule.class)).thenReturn(true);
        when(definition.stringValue(GraalPyModule.class)).thenReturn(Optional.of(moduleName));
        when(definition.getScopeName()).thenReturn(Optional.of("Singleton"));
        when(definition.getExecutableMethods()).thenReturn(List.of());
        return definition;
    }

    private static BeanDefinition<?> beanDefinition(Class<?> beanType) {
        BeanDefinition<?> definition = mock(BeanDefinition.class);
        when(definition.getBeanType()).then(invocation -> beanType);
        when(definition.getName()).thenReturn(beanType.getSimpleName());
        when(definition.hasStereotype(GraalPyModule.class)).thenReturn(false);
        when(definition.hasDeclaredStereotype(GraalPyModule.class)).thenReturn(false);
        when(definition.getScopeName()).thenReturn(Optional.empty());
        when(definition.getDeclaredQualifier()).thenReturn(null);
        when(definition.getExecutableMethods()).thenReturn(List.of());
        return definition;
    }

    @GraalPyModule("dealer")
    interface DealerService {
        String deal(String name, int count);

        CompletableFuture<String> dealAsync();
    }

    static final class ExplodingFactory implements GraalPyContextBuilderFactory {
        static final AtomicInteger createBuilderCalls = new AtomicInteger();

        @Override
        public Context.Builder createBuilder() {
            createBuilderCalls.incrementAndGet();
            throw new AssertionError("Panel rendering must not create GraalPy Context builders.");
        }
    }
}
