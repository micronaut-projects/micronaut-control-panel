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
import io.micronaut.context.python.GraalPyContextConfiguration;
import io.micronaut.context.python.PythonAsyncioConfiguration;
import io.micronaut.context.python.PythonConfiguration;
import io.micronaut.context.python.PythonContextExecutor;
import io.micronaut.context.python.PythonPoolConfiguration;
import io.micronaut.context.python.PythonPoolStatistics;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PythonRuntimeInspectorTest {

    @Test
    void masksSensitiveContextOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("python.PythonPath", "/app/src");
        options.put("python.AuthToken", "super-secret");
        options.put("engine.api-key", "super-secret");

        var configuration = inspector(options, List.of("java.util.*"), Map.of()).contextConfiguration();

        assertTrue(configuration.available());
        assertEquals(3, configuration.options().size());
        assertTrue(configuration.options().stream()
            .anyMatch(entry -> entry.key().equals("python.PythonPath") && entry.value().equals("/app/src") && !entry.masked()));
        assertTrue(configuration.options().stream()
            .allMatch(entry -> !entry.key().toLowerCase(java.util.Locale.ROOT).contains("token") || entry.masked()));
        assertTrue(configuration.options().stream()
            .noneMatch(entry -> entry.value().contains("super-secret")));
        assertEquals(List.of("java.util.*"), configuration.hostClassLookup());
    }

    @Test
    void listsEnvironmentKeysButNeverValues() {
        var configuration = inspector(Map.of(), List.of(), Map.of("api-key", "super-secret", "home", "/home/app"))
            .contextConfiguration();

        assertEquals(List.of("api-key", "home"), configuration.environmentKeys());
        assertTrue(configuration.hasEnvironmentKeys());
    }

    @Test
    void reportsUnrestrictedHostClassLookupAsAnEmptyList() {
        var configuration = inspector(Map.of(), List.of(), Map.of()).contextConfiguration();

        assertFalse(configuration.hasHostClassLookup());
    }

    @Test
    void readsPoolAndPythonConfigurationValues() {
        ApplicationContext beanContext = beanContext(Map.of(), List.of(), Map.of());
        doReturn(Optional.of(new PythonPoolConfiguration(true, 0, Duration.ofSeconds(5), 3)))
            .when(beanContext).findBean(PythonPoolConfiguration.class);
        doReturn(Optional.of(new PythonAsyncioConfiguration(true)))
            .when(beanContext).findBean(PythonAsyncioConfiguration.class);
        doReturn(Optional.of(new PythonConfiguration(true)))
            .when(beanContext).findBean(PythonConfiguration.class);

        var settings = new PythonRuntimeInspector(beanContext, executor(statistics(true, false))).contextPool().settings();

        assertEquals("true", settings.poolEnabled());
        assertEquals("0 (defaults to 2 x available processors)", settings.size());
        assertEquals("PT5S", settings.warnWait());
        assertEquals("3", settings.maxEventLoopContexts());
        assertEquals("true", settings.asyncioEnabled());
        assertEquals("true", settings.pythonEnabled());
    }

    @Test
    void hintsAtAnUndersizedPool() {
        var pool = new PythonRuntimeInspector(beanContext(Map.of(), List.of(), Map.of()), executor(statistics(true, false)))
            .contextPool();

        assertTrue(pool.hasHints());
        assertTrue(pool.hints().stream().anyMatch(hint -> hint.contains("pool is too small")));
    }

    @Test
    void hintsAtAnOversizedPool() {
        var pool = new PythonRuntimeInspector(beanContext(Map.of(), List.of(), Map.of()),
            executor(new PythonPoolStatistics(true, 4, 4, 4, 0, 4, 0, 0, 0, false))).contextPool();

        assertTrue(pool.hints().stream().anyMatch(hint -> hint.contains("larger than this workload needs")));
    }

    @Test
    void reportsUnavailableWhenStatisticsAreNull() {
        var pool = new PythonRuntimeInspector(beanContext(Map.of(), List.of(), Map.of()), executor(null)).contextPool();

        assertEquals("Unavailable", pool.state());
        assertFalse(pool.available());
        assertNull(pool.statistics());
    }

    private static PythonRuntimeInspector inspector(Map<String, String> options,
                                                    List<String> hostClassLookup,
                                                    Map<String, String> environment) {
        return new PythonRuntimeInspector(beanContext(options, hostClassLookup, environment), executor(statistics(true, false)));
    }

    private static ApplicationContext beanContext(Map<String, String> options,
                                                  List<String> hostClassLookup,
                                                  Map<String, String> environment) {
        ApplicationContext beanContext = mock(ApplicationContext.class);
        GraalPyContextConfiguration configuration = mock(GraalPyContextConfiguration.class);
        when(configuration.getOptions()).thenReturn(options);
        when(configuration.getHostClassLookup()).thenReturn(hostClassLookup);
        doReturn(Optional.of(configuration)).when(beanContext).findBean(GraalPyContextConfiguration.class);
        doReturn(Optional.empty()).when(beanContext).findBean(PythonPoolConfiguration.class);
        doReturn(Optional.empty()).when(beanContext).findBean(PythonAsyncioConfiguration.class);
        doReturn(Optional.empty()).when(beanContext).findBean(PythonConfiguration.class);
        doReturn(List.of()).when(beanContext).getAllBeanDefinitions();
        doReturn(PythonRuntimeInspectorTest.class.getClassLoader()).when(beanContext).getClassLoader();
        doReturn(Map.copyOf(environment)).when(beanContext).getProperties(any());
        return beanContext;
    }

    private static PythonPoolStatistics statistics(boolean enabled, boolean closed) {
        return new PythonPoolStatistics(enabled, 4, 3, 2, 1, 10, 2, 120, 90, closed);
    }

    private static PythonContextExecutor executor(PythonPoolStatistics statistics) {
        return new PythonContextExecutor() {
            @Override
            public <T> T withContext(Function<Context, T> function) {
                throw new AssertionError("Panel rendering must not borrow a GraalPy context");
            }

            @Override
            public PythonPoolStatistics statistics() {
                return statistics;
            }
        };
    }
}
