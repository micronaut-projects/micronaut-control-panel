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

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;

import java.util.Locale;

/**
 * Enables the TOML panel only when there is TOML data to inspect, unless empty diagnostics are requested.
 */
@Internal
public class TomlPropertySourcesCondition implements Condition {

    private static final String TOML_EXTENSION = ".toml";

    @Override
    public boolean matches(ConditionContext context) {
        Environment environment = context.getBean(Environment.class);
        if (environment.getProperty(TomlPanelConfiguration.SHOW_EMPTY_PROPERTY, Boolean.class).orElse(false)) {
            return true;
        }
        return environment.getPropertySources()
            .stream()
            .anyMatch(TomlPropertySourcesCondition::isTomlPropertySource);
    }

    static boolean isTomlPropertySource(PropertySource propertySource) {
        String location = propertySource.getOrigin().location();
        return StringUtils.isNotEmpty(location) && hasTomlExtension(location.toLowerCase(Locale.ROOT));
    }

    private static boolean hasTomlExtension(String location) {
        int extensionStart = location.indexOf(TOML_EXTENSION);
        while (extensionStart >= 0) {
            int next = extensionStart + TOML_EXTENSION.length();
            if (isPathBoundary(location, extensionStart) && isExtensionBoundary(location, next)) {
                return true;
            }
            extensionStart = location.indexOf(TOML_EXTENSION, next);
        }
        return false;
    }

    private static boolean isPathBoundary(String location, int extensionStart) {
        if (extensionStart == 0) {
            return false;
        }
        char previous = location.charAt(extensionStart - 1);
        return previous != '/' && previous != '\\' && previous != ':';
    }

    private static boolean isExtensionBoundary(String location, int index) {
        return index == location.length()
            || switch (location.charAt(index)) {
                case '?', '#', ')', '!', ' ', '\t', '\n', '\r' -> true;
                default -> false;
            };
    }
}
