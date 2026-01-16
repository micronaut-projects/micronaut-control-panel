/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.core.panels;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutesControlPanelTest {

    private ApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.run();
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) ctx.stop();
    }

    @Test
    void itIsConfiguredCorrectly() {
        ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(RoutesControlPanel.NAME));
        assertTrue(cfg.isEnabled());

        RoutesControlPanel panel = ctx.getBean(RoutesControlPanel.class);
        assertEquals("HTTP Routes", panel.getTitle());
        assertEquals("fa-route", panel.getIcon());
        assertEquals(20, panel.getOrder());
        assertEquals(0, panel.getBody().appRoutes().size());

        assertTrue(panel.getBody().micronautRoutes().size() > 0);
        assertTrue(Integer.parseInt(panel.getBadge()) > 0);
        assertTrue(panel.getBody().openApiViewers().isEmpty());
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext local = ApplicationContext.run(java.util.Map.of(RoutesControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = local.getBean(ControlPanelConfiguration.class, Qualifiers.byName(RoutesControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(local.containsBean(RoutesControlPanel.class));
        }
    }

    @Test
    void routesAreNotDuplicated() {
        RoutesControlPanel panel = ctx.getBean(RoutesControlPanel.class);
        var body = panel.getBody();
        assertTrue(body.appRoutes().size() >= 0);
        assertTrue(body.micronautRoutes().size() > 0);

        var allRoutes = new java.util.ArrayList<>(body.appRoutes().values().stream().flatMap(java.util.Collection::stream).toList());
        allRoutes.addAll(body.micronautRoutes().values().stream().flatMap(java.util.Collection::stream).toList());

        var routeSignatures = allRoutes.stream()
                .map(route -> route.getHttpMethodName() + "-" + route.getUriMatchTemplate().toPathString() + "-" + route.getTargetMethod().getName())
                .toList();

        assertEquals(routeSignatures.size(), new java.util.HashSet<>(routeSignatures).size());
        assertTrue(allRoutes.size() > 0);

        // new: verify lists are unmodifiable
        var grouped = body.micronautRoutes();
        var firstKey = grouped.keySet().stream().findFirst().orElse(null);
        if (firstKey != null) {
            var list = grouped.get(firstKey);
            assertThrows(UnsupportedOperationException.class, () -> list.add(null));
        }
    }

    @Test
    void itDetectsSwaggerUiViewer() {
        try (ApplicationContext local = ApplicationContext.run(java.util.Map.of(
                "micronaut.router.static-resources.swagger-ui.paths", "classpath:META-INF/swagger/views/swagger-ui",
                "micronaut.router.static-resources.swagger-ui.mapping", "/swagger-ui/**"
        ))) {
            RoutesControlPanel panel = local.getBean(RoutesControlPanel.class);
            var viewers = panel.getBody().openApiViewers();
            assertEquals(1, viewers.size());
            assertEquals("swagger-ui", viewers.getFirst().name());
            assertEquals("Swagger UI", viewers.getFirst().label());
            assertEquals("si-swagger", viewers.getFirst().icon());
            assertEquals("/swagger-ui", viewers.getFirst().uri());
        }
    }

    @Test
    void itDetectsMultipleViewers() {
        try (ApplicationContext local = ApplicationContext.run(java.util.Map.of(
                "micronaut.router.static-resources.swagger-ui.paths", "classpath:META-INF/swagger/views/swagger-ui",
                "micronaut.router.static-resources.swagger-ui.mapping", "/swagger-ui/**",
                "micronaut.router.static-resources.redoc.paths", "classpath:META-INF/swagger/views/redoc",
                "micronaut.router.static-resources.redoc.mapping", "/redoc/**"
        ))) {
            RoutesControlPanel panel = local.getBean(RoutesControlPanel.class);
            var viewers = panel.getBody().openApiViewers();
            assertEquals(2, viewers.size());

            var names = viewers.stream().map(RoutesControlPanel.OpenApiViewerLink::name).toList();
            assertTrue(names.contains("swagger-ui"));
            assertTrue(names.contains("redoc"));
        }
    }

    @Test
    void itIgnoresNonOpenApiStaticResources() {
        try (ApplicationContext local = ApplicationContext.run(java.util.Map.of(
                "micronaut.router.static-resources.css.paths", "classpath:static/css",
                "micronaut.router.static-resources.css.mapping", "/css/**",
                "micronaut.router.static-resources.js.paths", "classpath:static/js",
                "micronaut.router.static-resources.js.mapping", "/js/**"
        ))) {
            RoutesControlPanel panel = local.getBean(RoutesControlPanel.class);
            var viewers = panel.getBody().openApiViewers();
            assertTrue(viewers.isEmpty());
        }
    }

    @Test
    void itCleansUriMappingCorrectly() {
        try (ApplicationContext local = ApplicationContext.run(java.util.Map.of(
                "micronaut.router.static-resources.swagger-ui.paths", "classpath:META-INF/swagger/views/swagger-ui",
                "micronaut.router.static-resources.swagger-ui.mapping", "/api/docs/swagger-ui/**",
                "micronaut.router.static-resources.rapidoc.paths", "classpath:META-INF/swagger/views/rapidoc",
                "micronaut.router.static-resources.rapidoc.mapping", "/rapidoc/*"
        ))) {
            RoutesControlPanel panel = local.getBean(RoutesControlPanel.class);
            var viewers = panel.getBody().openApiViewers();
            assertEquals(2, viewers.size());

            var swaggerUi = viewers.stream().filter(v -> v.name().equals("swagger-ui")).findFirst().orElseThrow();
            assertEquals("/api/docs/swagger-ui", swaggerUi.uri());

            var rapidoc = viewers.stream().filter(v -> v.name().equals("rapidoc")).findFirst().orElseThrow();
            assertEquals("/rapidoc", rapidoc.uri());
        }
    }

    @Controller
    static class DummyController {
        @Get
        Object index() { return null; }
    }
}
