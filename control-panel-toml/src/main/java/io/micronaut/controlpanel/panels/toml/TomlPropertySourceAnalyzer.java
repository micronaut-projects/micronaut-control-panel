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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the TOML diagnostics model from the runtime Environment property sources.
 */
@Internal
@Singleton
public class TomlPropertySourceAnalyzer {

    private static final String MASKED_VALUE = "*****";
    private static final Set<String> SENSITIVE_TERMS = Set.of(
        "password",
        "credential",
        "certificate",
        "key",
        "secret",
        "token"
    );

    /**
     * Creates a TOML property-source analyzer.
     */
    public TomlPropertySourceAnalyzer() {
    }

    /**
     * Analyze the current environment.
     *
     * @param environment The environment
     * @param configuration TOML panel configuration
     * @return TOML diagnostics body
     */
    public TomlConfigurationControlPanel.Body analyze(Environment environment, TomlPanelConfiguration configuration) {
        List<PropertySource> propertySources = new ArrayList<>(environment.getPropertySources());
        List<TomlKeyEntryBuilder> keyBuilders = new ArrayList<>();
        List<TomlSourceBuilder> sourceBuilders = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();

        int precedence = 1;
        for (PropertySource propertySource : propertySources) {
            if (!isTomlPropertySource(propertySource)) {
                precedence++;
                continue;
            }
            String name = propertySource.getName();
            TomlSourceBuilder sourceBuilder = new TomlSourceBuilder(
                name,
                originLocation(propertySource),
                propertySource.getOrder(),
                precedence
            );
            sourceBuilders.add(sourceBuilder);
            for (String key : sortedKeys(propertySource)) {
                Object value = propertySource.get(key);
                Optional<PropertySource> overridingPropertySource = findOverridingSource(propertySources, propertySource, key);
                TomlKeyStatus status = resolveStatus(environment, overridingPropertySource, key, value);
                String overridingSource = overridingPropertySource.map(PropertySource::getName).orElse("");
                String displayValue = displayValue(key, value, status, configuration);
                sourceBuilder.add(status);
                uniqueKeys.add(key);
                keyBuilders.add(new TomlKeyEntryBuilder(
                    key,
                    valueType(value),
                    displayValue,
                    name,
                    originLocation(propertySource),
                    status,
                    overridingSource,
                    statusLabel(status, overridingSource)
                ));
            }
            precedence++;
        }

        List<TomlConfigurationControlPanel.TomlSource> sources = sourceBuilders
            .stream()
            .map(TomlSourceBuilder::build)
            .toList();
        List<TomlConfigurationControlPanel.TomlKeyEntry> keys = keyBuilders.stream()
            .map(TomlKeyEntryBuilder::build)
            .toList();
        TomlConfigurationControlPanel.Summary summary = new TomlConfigurationControlPanel.Summary(
            sources.size(),
            keys.size(),
            uniqueKeys.size(),
            countStatus(keys, TomlKeyStatus.EFFECTIVE),
            countOverridden(keys),
            countStatus(keys, TomlKeyStatus.UNKNOWN)
        );
        return new TomlConfigurationControlPanel.Body(
            environment.getActiveNames(),
            summary,
            sources,
            keys,
            configuration.isShowValues(),
            configuration.isShowOverriddenValues(),
            !sources.isEmpty()
        );
    }

    static boolean isTomlPropertySource(PropertySource propertySource) {
        return TomlPropertySourcesCondition.isTomlPropertySource(propertySource);
    }

    private static List<String> sortedKeys(PropertySource propertySource) {
        List<String> keys = new ArrayList<>();
        propertySource.forEach(keys::add);
        keys.sort(String::compareTo);
        return keys;
    }

    private static String originLocation(PropertySource propertySource) {
        String location = propertySource.getOrigin().location();
        if (location == null || location.isBlank()) {
            return "Unknown origin";
        }
        return location;
    }

    private static TomlKeyStatus resolveStatus(Environment environment,
                                               Optional<PropertySource> overridingSource,
                                               String key,
                                               @Nullable Object value) {
        if (overridingSource.isPresent()) {
            return isTomlPropertySource(overridingSource.get())
                ? TomlKeyStatus.OVERRIDDEN_BY_TOML
                : TomlKeyStatus.OVERRIDDEN_BY_NON_TOML;
        }
        Optional<Object> resolvedValue = environment.getProperty(key, Object.class);
        if (resolvedValue.isEmpty()) {
            return TomlKeyStatus.UNKNOWN;
        }
        return valuesEquivalent(resolvedValue.get(), value) ? TomlKeyStatus.EFFECTIVE : TomlKeyStatus.UNKNOWN;
    }

    private static Optional<PropertySource> findOverridingSource(List<PropertySource> propertySources,
                                                                  PropertySource tomlSource,
                                                                  String key) {
        for (PropertySource propertySource : propertySources) {
            if (propertySource == tomlSource) {
                return Optional.empty();
            }
            if (containsKey(propertySource, key)) {
                return Optional.of(propertySource);
            }
        }
        return Optional.empty();
    }

    private static boolean containsKey(PropertySource propertySource, String key) {
        for (String candidate : propertySource) {
            if (candidate.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean valuesEquivalent(@Nullable Object resolvedValue, @Nullable Object sourceValue) {
        return Objects.equals(resolvedValue, sourceValue) || formatValue(resolvedValue).equals(formatValue(sourceValue));
    }

    private static String displayValue(String key,
                                       @Nullable Object value,
                                       TomlKeyStatus status,
                                       TomlPanelConfiguration configuration) {
        if (isSensitive(key) || !configuration.isShowValues()) {
            return MASKED_VALUE;
        }
        if (status != TomlKeyStatus.EFFECTIVE && !configuration.isShowOverriddenValues()) {
            return MASKED_VALUE;
        }
        return formatValue(value);
    }

    static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_TERMS.stream().anyMatch(lower::contains);
    }

    private static String formatValue(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values.toString();
        }
        return value.toString();
    }

    private static String valueType(@Nullable Object value) {
        if (value == null) {
            return "Unknown";
        }
        if (value instanceof CharSequence) {
            return "String";
        }
        if (value instanceof Boolean) {
            return "Boolean";
        }
        if (value instanceof Number) {
            return "Number";
        }
        if (value instanceof Collection<?> || value.getClass().isArray()) {
            return "List";
        }
        if (value instanceof Map<?, ?>) {
            return "Map";
        }
        return value.getClass().getSimpleName();
    }

    private static String statusLabel(TomlKeyStatus status, String overridingSource) {
        return switch (status) {
            case EFFECTIVE -> "Effective";
            case OVERRIDDEN_BY_TOML -> "Overridden by TOML source " + overridingSource;
            case OVERRIDDEN_BY_NON_TOML -> "Overridden by non-TOML source " + overridingSource;
            case UNKNOWN -> "Unknown";
        };
    }

    private static int countStatus(List<TomlConfigurationControlPanel.TomlKeyEntry> keys, TomlKeyStatus status) {
        return (int) keys.stream().filter(key -> key.status() == status).count();
    }

    private static int countOverridden(List<TomlConfigurationControlPanel.TomlKeyEntry> keys) {
        return (int) keys.stream().filter(key -> isOverridden(key.status())).count();
    }

    private static boolean isOverridden(TomlKeyStatus status) {
        return status == TomlKeyStatus.OVERRIDDEN_BY_TOML || status == TomlKeyStatus.OVERRIDDEN_BY_NON_TOML;
    }

    /**
     * TOML key resolution status.
     */
    @ReflectiveAccess
    public enum TomlKeyStatus {
        /**
         * The TOML source currently contributes the resolved environment value.
         */
        EFFECTIVE,
        /**
         * A higher-precedence TOML source contains the same key.
         */
        OVERRIDDEN_BY_TOML,
        /**
         * A higher-precedence non-TOML source contains the same key.
         */
        OVERRIDDEN_BY_NON_TOML,
        /**
         * The available Environment data cannot prove the row status.
         */
        UNKNOWN
    }

    private static final class TomlSourceBuilder {
        private final String name;
        private final String origin;
        private final int order;
        private final int precedence;
        private int keyCount;
        private int effectiveCount;
        private int overriddenCount;
        private int unknownCount;

        private TomlSourceBuilder(String name, String origin, int order, int precedence) {
            this.name = name;
            this.origin = origin;
            this.order = order;
            this.precedence = precedence;
        }

        private void add(TomlKeyStatus status) {
            keyCount++;
            if (status == TomlKeyStatus.EFFECTIVE) {
                effectiveCount++;
            } else if (isOverridden(status)) {
                overriddenCount++;
            } else if (status == TomlKeyStatus.UNKNOWN) {
                unknownCount++;
            }
        }

        private TomlConfigurationControlPanel.TomlSource build() {
            return new TomlConfigurationControlPanel.TomlSource(
                name,
                origin,
                order,
                precedence,
                keyCount,
                effectiveCount,
                overriddenCount,
                unknownCount
            );
        }
    }

    private record TomlKeyEntryBuilder(String key,
                                       String type,
                                       String displayValue,
                                       String sourceName,
                                       String sourceOrigin,
                                       TomlKeyStatus status,
                                       String overridingSource,
                                       String statusLabel) {
        private TomlConfigurationControlPanel.TomlKeyEntry build() {
            return new TomlConfigurationControlPanel.TomlKeyEntry(
                key,
                type,
                displayValue,
                sourceName,
                sourceOrigin,
                status,
                overridingSource,
                statusLabel
            );
        }
    }
}
