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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class TomlClasspathAbsenceTest {

    @Test
    void tomlClasspathAbsencePreventsPanel() {
        try (ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test")
            .propertySources(
                PropertySource.of("test-config", Map.of("micronaut.control-panel.toml.show-empty", true)),
                PropertySource.of(
                    "application",
                    Map.of("micronaut.application.name", "demo"),
                    PropertySource.Origin.of("classpath:application.toml"),
                    100
                )
            )
            .start()) {
            assertFalse(context.containsBean(TomlConfigurationControlPanel.class));
        }
    }
}
