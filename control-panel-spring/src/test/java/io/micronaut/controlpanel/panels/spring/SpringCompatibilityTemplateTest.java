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

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpringCompatibilityTemplateTest {

    @Test
    void detailTemplateRendersGenericDiagnosticPanelLinks() throws IOException {
        var handlebars = new Handlebars(new ClassPathTemplateLoader("/", ".hbs"));
        var template = handlebars.compile("views/spring-compatibility/detail");
        String html = template.apply(Map.of(
            "ext", Map.of("controlPanelPath", "/control-panel"),
            "controlPanel", Map.of("body", emptyBody())
        ));

        assertTrue(html.contains("href=\"/control-panel/beans\""));
        assertTrue(html.contains("href=\"/control-panel/routes\""));
        assertTrue(html.contains("href=\"/control-panel/disabled-beans\""));
        assertTrue(html.contains("Compare with generic diagnostics"));
    }

    private SpringCompatibilityBody emptyBody() {
        return new SpringCompatibilityBody(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            false,
            "No Spring-origin metadata was detected."
        );
    }
}
