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
import io.micronaut.context.env.PropertySource;
import io.micronaut.controlpanel.panels.toml.TomlPropertySourceAnalyzer.TomlKeyStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
}
