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
package io.micronaut.controlpanel.panels.spring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpringCompatibilityAvailableConditionTest {

    @Test
    void detectsSpringMarkerClassesOnCurrentClasspath() {
        assertTrue(SpringCompatibilityAvailableCondition.isMicronautSpringPresent(getClass().getClassLoader()));
    }

    @Test
    void returnsFalseWhenSpringMarkerClassesAreAbsent() {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
            assertFalse(SpringCompatibilityAvailableCondition.isMicronautSpringPresent(classLoader));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void conditionIsInitializedAtBuildTimeForNativeImage() throws IOException {
        String resourceName = "META-INF/native-image/io.micronaut.controlpanel/micronaut-control-panel-spring/native-image.properties";
        URL resource = getClass().getClassLoader().getResource(resourceName);

        assertNotNull(resource);
        try (var input = resource.openStream()) {
            String properties = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(properties.contains("--initialize-at-build-time=" + SpringCompatibilityAvailableCondition.class.getName()));
        }
    }
}
