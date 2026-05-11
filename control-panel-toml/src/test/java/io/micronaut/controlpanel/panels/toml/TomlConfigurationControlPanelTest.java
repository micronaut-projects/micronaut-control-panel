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
package io.micronaut.controlpanel.panels.toml;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.controlpanel.panels.toml.TomlPropertySourceAnalyzer.TomlKeyStatus;
import io.micronaut.inject.BeanConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TomlConfigurationControlPanelTest {

    @Test
    void activeTomlSourceCreatesPanel() {
        try (ApplicationContext context = context(
            tomlSource("application", 100, Map.of(
                "micronaut.application.name", "demo",
                "datasources.default.enabled", true
            ))
        )) {
            assertTrue(context.containsBean(TomlConfigurationControlPanel.class));

            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();
            assertEquals(1, body.summary().sourceCount());
            assertEquals(2, body.summary().uniqueKeys());
            assertEquals(2, body.summary().effectiveRows());
            assertEquals(0, body.summary().overriddenRows());
            assertEquals("1 sources / 2 keys", context.getBean(TomlConfigurationControlPanel.class).getBadge());
        }
    }

    @Test
    void loadedTomlConfigurationCreatesPanel() {
        try (ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test", "toml-panel")
            .start()) {
            assertTrue(context.containsBean(TomlConfigurationControlPanel.class));

            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();
            assertTrue(body.sources().stream().anyMatch(source -> source.origin().contains("application-toml-panel.toml")));
            assertTrue(body.keys().stream().anyMatch(entry -> entry.key().equals("toml.panel.loaded")));
        }
    }

    @Test
    void multipleTomlSourcesReportTomlOverrides() {
        try (ApplicationContext context = context(
            tomlSource("application-dev", 300, Map.of(
                "micronaut.application.name", "dev",
                "feature.enabled", true
            )),
            tomlSource("application", 100, Map.of(
                "micronaut.application.name", "base",
                "server.port", 8080
            ))
        )) {
            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();

            assertEquals(2, body.summary().sourceCount());
            assertEquals(3, body.summary().uniqueKeys());
            assertEquals(1, body.summary().overriddenRows());
            TomlConfigurationControlPanel.TomlKeyEntry baseName = findKey(body.keys(), "application", "micronaut.application.name").orElseThrow();
            assertEquals(TomlKeyStatus.OVERRIDDEN_BY_TOML, baseName.status());
            assertEquals("application-dev", baseName.overridingSource());
        }
    }

    @Test
    void duplicateTomlSourceNamesArePreserved() {
        PropertySource profileSource = propertySource(
            "application",
            300,
            "classpath:application-dev.toml",
            Map.of("micronaut.application.name", "dev")
        );
        PropertySource baseSource = propertySource(
            "application",
            100,
            "classpath:application.toml",
            Map.of("server.port", 8080)
        );
        TomlConfigurationControlPanel.Body body = new TomlPropertySourceAnalyzer()
            .analyze(new UnknownValueEnvironment(List.of(profileSource, baseSource)), new TomlPanelConfiguration());

        assertEquals(2, body.summary().sourceCount());
        assertEquals(2, body.sources().size());
        assertTrue(body.sources().stream().anyMatch(source -> source.origin().equals("classpath:application-dev.toml")));
        assertTrue(body.sources().stream().anyMatch(source -> source.origin().equals("classpath:application.toml")));
        assertEquals(2, body.keys().size());
    }

    @Test
    void tomlSourceDetectionRequiresTomlExtensionBoundary() {
        try (ApplicationContext context = context(
            propertySource("application-backup", 100, "classpath:application.toml.bak", Map.of("micronaut.application.name", "demo"))
        )) {
            assertFalse(context.containsBean(TomlConfigurationControlPanel.class));
        }

        assertTrue(TomlPropertySourcesCondition.isTomlPropertySource(
            propertySource("application", 100, "classpath:APPLICATION.TOML?profile=dev", Map.of("micronaut.application.name", "demo"))
        ));
        assertFalse(TomlPropertySourcesCondition.isTomlPropertySource(
            propertySource("application-backup", 100, "classpath:application.toml.bak", Map.of("micronaut.application.name", "demo"))
        ));
    }

    @Test
    void nonTomlPropertySourceOverridesTomlKey() {
        try (ApplicationContext context = context(
            propertySource("system", 400, "system properties", Map.of("datasources.default.url", "jdbc:h2:mem:override")),
            tomlSource("application", 100, Map.of("datasources.default.url", "jdbc:h2:mem:toml"))
        )) {
            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();
            TomlConfigurationControlPanel.TomlKeyEntry entry = body.keys().get(0);

            assertEquals(TomlKeyStatus.OVERRIDDEN_BY_NON_TOML, entry.status());
            assertEquals("system", entry.overridingSource());
            assertEquals(1, body.summary().overriddenRows());
        }
    }

    @Test
    void noTomlSourcePreventsPanelUnlessShowEmptyIsEnabled() {
        try (ApplicationContext context = context(
            propertySource("application", 100, "classpath:application.yml", Map.of("micronaut.application.name", "demo"))
        )) {
            assertFalse(context.containsBean(TomlConfigurationControlPanel.class));
        }

        try (ApplicationContext context = context(
            config(Map.of("micronaut.control-panel.toml.show-empty", true)),
            propertySource("application", 100, "classpath:application.yml", Map.of("micronaut.application.name", "demo"))
        )) {
            assertTrue(context.containsBean(TomlConfigurationControlPanel.class));
            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();
            assertFalse(body.hasTomlSources());
            assertEquals(0, body.summary().sourceCount());
        }
    }

    @Test
    void valueMaskingKeepsSensitiveKeysMasked() {
        try (ApplicationContext context = context(
            config(Map.of(
                "micronaut.control-panel.toml.show-values", true,
                "micronaut.control-panel.toml.show-overridden-values", true
            )),
            tomlSource("application", 100, Map.of(
                "api.secret", "do-not-render",
                "micronaut.application.name", "demo"
            ))
        )) {
            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();

            assertEquals("*****", findKey(body.keys(), "application", "api.secret").orElseThrow().displayValue());
            assertEquals("demo", findKey(body.keys(), "application", "micronaut.application.name").orElseThrow().displayValue());
        }

        try (ApplicationContext context = context(
            tomlSource("application", 100, Map.of("micronaut.application.name", "demo"))
        )) {
            TomlConfigurationControlPanel.Body body = context.getBean(TomlConfigurationControlPanel.class).getBody();

            assertEquals("*****", findKey(body.keys(), "application", "micronaut.application.name").orElseThrow().displayValue());
        }
    }

    @Test
    void unknownValuesRequireOverriddenValueOptIn() {
        TomlPanelConfiguration configuration = new TomlPanelConfiguration();
        configuration.setShowValues(true);
        PropertySource tomlSource = tomlSource("application", 100, Map.of("feature.unresolved", "do-not-render"));
        TomlConfigurationControlPanel.Body body = new TomlPropertySourceAnalyzer()
            .analyze(new UnknownValueEnvironment(List.of(tomlSource)), configuration);

        TomlConfigurationControlPanel.TomlKeyEntry entry = findKey(body.keys(), "application", "feature.unresolved").orElseThrow();
        assertEquals(TomlKeyStatus.UNKNOWN, entry.status());
        assertEquals("*****", entry.displayValue());

        configuration.setShowOverriddenValues(true);
        body = new TomlPropertySourceAnalyzer().analyze(new UnknownValueEnvironment(List.of(tomlSource)), configuration);
        assertEquals("do-not-render", findKey(body.keys(), "application", "feature.unresolved").orElseThrow().displayValue());
    }

    private static Optional<TomlConfigurationControlPanel.TomlKeyEntry> findKey(List<TomlConfigurationControlPanel.TomlKeyEntry> keys,
                                                                                String sourceName,
                                                                                String key) {
        return keys.stream()
            .filter(entry -> entry.sourceName().equals(sourceName))
            .filter(entry -> entry.key().equals(key))
            .findFirst();
    }

    private static ApplicationContext context(PropertySource... propertySources) {
        return ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test")
            .propertySources(propertySources)
            .start();
    }

    private static PropertySource config(Map<String, Object> properties) {
        return propertySource("test-config", 1_000, "test configuration", properties);
    }

    private static PropertySource tomlSource(String name, int order, Map<String, Object> properties) {
        return propertySource(name, order, "classpath:" + name + ".toml", properties);
    }

    private static PropertySource propertySource(String name, int order, String origin, Map<String, Object> properties) {
        return PropertySource.of(name, properties, PropertySource.Origin.of(origin), order);
    }

    private record UnknownValueEnvironment(Collection<PropertySource> propertySources) implements Environment {
        @Override
        public Set<String> getActiveNames() {
            return Set.of("test");
        }

        @Override
        public Collection<PropertySource> getPropertySources() {
            return propertySources;
        }

        @Override
        public Environment addPropertySource(PropertySource propertySource) {
            throw unsupported();
        }

        @Override
        public Environment removePropertySource(PropertySource propertySource) {
            throw unsupported();
        }

        @Override
        public Environment addPackage(String pkg) {
            throw unsupported();
        }

        @Override
        public Environment addConfigurationExcludes(String... configurations) {
            throw unsupported();
        }

        @Override
        public Environment addConfigurationIncludes(String... configurations) {
            throw unsupported();
        }

        @Override
        public Collection<String> getPackages() {
            return Collections.emptyList();
        }

        @Override
        public PropertyPlaceholderResolver getPlaceholderResolver() {
            throw unsupported();
        }

        @Override
        public Map<String, Object> refreshAndDiff() {
            throw unsupported();
        }

        @Override
        public boolean isActive(BeanConfiguration configuration) {
            return false;
        }

        @Override
        public Collection<PropertySourceLoader> getPropertySourceLoaders() {
            return Collections.emptyList();
        }

        @Override
        public MutableConversionService getConversionService() {
            throw unsupported();
        }

        @Override
        public boolean containsProperty(String name) {
            return false;
        }

        @Override
        public boolean containsProperties(String name) {
            return false;
        }

        @Override
        public <T> Optional<T> getProperty(String name, ArgumentConversionContext<T> conversionContext) {
            return Optional.empty();
        }

        @Override
        public Collection<List<String>> getPropertyPathMatches(String pathPattern) {
            return Collections.emptyList();
        }

        @Override
        public Optional<InputStream> getResourceAsStream(String path) {
            return Optional.empty();
        }

        @Override
        public Optional<URL> getResource(String path) {
            return Optional.empty();
        }

        @Override
        public Stream<URL> getResources(String path) {
            return Stream.empty();
        }

        @Override
        public boolean supportsPrefix(String path) {
            return false;
        }

        @Override
        public Environment forBase(String basePath) {
            throw unsupported();
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not needed for this test");
        }
    }
}
