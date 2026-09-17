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
package io.micronaut.controlpanel.panels.reactor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.web.router.Router;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorControlPanelTest {

    private static final String SPEC_NAME = "ReactorControlPanelTest";

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ReactorControlPanel.NAME));
            assertTrue(cfg.isEnabled());

            ReactorControlPanel panel = ctx.getBean(ReactorControlPanel.class);
            assertEquals("Reactor Diagnostics", panel.getTitle());
            assertEquals("fa-wave-square", panel.getIcon());
            assertEquals(25, panel.getOrder());
            assertEquals(String.valueOf(panel.getBody().routes().size()), panel.getBadge());
            assertEquals("reactor", panel.getCategory().id());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            ReactorControlPanel.ENABLED_PROPERTY, false
        ))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ReactorControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(ReactorControlPanel.class));
        }
    }

    @Test
    void listsOnlyReactorRoutesAndClassifiesModes() {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            ReactorDiagnosticsBody body = ctx.getBean(ReactorControlPanel.class).getBody();
            assertFalse(ctx.getBean(Router.class).uriRoutes().toList().isEmpty(), () -> ctx.getBean(Router.class).uriRoutes().toList().toString());
            assertFalse(body.routes().isEmpty(), () -> body.errors() + " " + ctx.getBean(Router.class).uriRoutes()
                .map(route -> route.getUriMatchTemplate().toPathString() + " return=" + route.getReturnType().asArgument().getTypeString(false)
                    + " response=" + route.getResponseBodyType().getTypeString(false)
                    + " reactive=" + route.isReactive())
                .toList()
                .toString());

            assertTrue(body.routes().stream().noneMatch(route -> route.uri().equals("/reactor/plain")));
            assertTrue(body.routes().stream().noneMatch(route -> route.uri().equals("/reactor/completion-stage")));
            assertRoute(body, "/reactor/mono", "single", "reactor.core.publisher.Mono<java.lang.String>");
            assertRoute(body, "/reactor/flux", "multi", "reactor.core.publisher.Flux<java.lang.String>");
            assertRoute(body, "/reactor/sse", "sse", "reactor.core.publisher.Flux<java.lang.String>");
            assertRoute(body, "/reactor/sse-with-parameters", "sse", "reactor.core.publisher.Flux<java.lang.String>");
            assertRoute(body, "/reactor/stream", "streaming", "reactor.core.publisher.Flux<java.lang.String>");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void reportsReactorHttpClientBeanDefinitionsWithoutEndpointData() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            ReactorDiagnosticsBody body = ctx.getBean(ReactorControlPanel.class).getBody();

            assertTrue(body.clients().stream().anyMatch(client -> client.beanName().contains("testReactorClient")
                && client.type().equals("ReactorHttpClient")), () -> body.clients().toString());
            assertTrue(body.clients().stream()
                .flatMap(client -> java.util.stream.Stream.of(client.beanName(), client.type(), client.qualifier()))
                .noneMatch(value -> value.contains("http://")
                    || value.contains("https://")
                    || value.contains("user:pass")
                    || value.contains("internal.example")
                    || value.contains("Authorization")));
            assertTrue(body.clients().stream().anyMatch(client -> client.beanName().equals("redacted")
                && client.type().equals("ReactorHttpClient")), () -> body.clients().toString());
            assertTrue(body.clients().stream().allMatch(client -> client.qualifier().isEmpty()));
        }
    }

    @Test
    void sectionErrorsDoNotExposeExceptionMessages() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            ReactorControlPanel panel = new ReactorControlPanel(
                ctx.getBean(ReactorRouteAnalyzer.class),
                () -> {
                    throw new IllegalStateException("https://user:pass@internal.example?token=secret");
                },
                ctx.getBean(ReactorReadinessInspector.class),
                ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ReactorControlPanel.NAME))
            );

            ReactorDiagnosticsBody body = panel.getBody();

            assertTrue(body.errors().stream().anyMatch(error -> error.section().equals("HTTP clients")
                && error.message().equals("Reactor HTTP client diagnostics are unavailable.")), () -> body.errors().toString());
            assertTrue(body.errors().stream()
                .map(ReactorDiagnosticsBody.SectionError::message)
                .noneMatch(message -> message.contains("https://")
                    || message.contains("user:pass")
                    || message.contains("internal.example")
                    || message.contains("secret")
                    || message.contains("token")), () -> body.errors().toString());
        }
    }

    @Test
    void reportsMissingOptionalBeansAsNotPresent() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "ReactorControlPanelNoOptionalBeans"))) {
            ReactorDiagnosticsBody body = ctx.getBean(ReactorControlPanel.class).getBody();

            assertTrue(body.clients().isEmpty());
            assertTrue(body.readiness().stream().anyMatch(check -> check.name().equals("MeterRegistry bean")
                && check.status().equals("not present")));
        }
    }

    @Test
    void reportsMeterRegistryAndReactorMeterNames() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "reactor.meters", true))) {
            ctx.getBean(MeterRegistry.class).counter("reactor.test").increment();

            ReactorDiagnosticsBody body = ctx.getBean(ReactorControlPanel.class).getBody();

            assertTrue(body.readiness().stream().anyMatch(check -> check.name().equals("MeterRegistry bean")
                && check.status().equals("present")));
            assertTrue(body.readiness().stream().anyMatch(check -> check.name().equals("Reactor meter names")
                && check.status().equals("present")
                && check.detail().contains("1 reactor.*")));
        }
    }

    private static void assertRoute(ReactorDiagnosticsBody body, String uri, String mode, String returnType) {
        assertTrue(body.routes().stream().anyMatch(route -> route.uri().equals(uri)
            && route.mode().equals(mode)
            && route.returnType().equals(returnType)), () -> "Missing " + uri + " as " + mode + " with " + returnType + " in " + body.routes());
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReactorClientFactory {

        @Singleton
        @Named("testReactorClient")
        ReactorHttpClient testReactorClient() {
            return null;
        }

        @Singleton
        @Named("https://user:pass@internal.example")
        ReactorHttpClient unsafeReactorClient() {
            return null;
        }
    }

    @Factory
    @Requires(property = "reactor.meters", value = "true")
    static class MeterRegistryFactory {

        @Singleton
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
