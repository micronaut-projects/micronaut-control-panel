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
package io.micronaut.controlpanel.panels.tracing;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracingTemplateTest {

    @Test
    void renderedDetailTemplateDoesNotExposeRawSecrets() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
                "otel.traces.exporter", "otlp",
                "otel.exporter.otlp.endpoint", "https://user:password@collector.example:4317?api-key=prod-secret&clientSecret=client-secret&token=raw-token",
                "otel.resource.attributes", "api.key=resource-secret"
        ))) {
            TracingBody body = new TracingDiagnosticsService(
                    context.getEnvironment(),
                    context.getBean(io.micronaut.runtime.ApplicationConfiguration.class),
                    context,
                    new TracingRedactor(),
                    Set.of("io.opentelemetry.api.OpenTelemetry")::contains
            ).getBody();
            Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader("/", ".hbs"));

            String html = handlebars.compile("views/tracing/detail").apply(Map.of("controlPanel", Map.of("body", body)));

            assertTrue(html.contains(TracingRedactor.REDACTED));
            assertFalse(html.contains("user:password"));
            assertFalse(html.contains("prod-secret"));
            assertFalse(html.contains("client-secret"));
            assertFalse(html.contains("raw-token"));
            assertFalse(html.contains("resource-secret"));
        }
    }
}
