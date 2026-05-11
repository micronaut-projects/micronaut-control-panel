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
package io.micronaut.controlpanel.panels.views;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.io.Writable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.View;
import io.micronaut.views.ViewsRenderer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewsControlPanelTest {

    private ApplicationContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.stop();
        }
    }

    @Test
    void itIsConfiguredCorrectly() {
        ctx = ApplicationContext.run(Map.of("micronaut.views.folder", "templates"));

        ControlPanelConfiguration configuration = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ViewsControlPanel.NAME));
        ViewsControlPanel panel = ctx.getBean(ViewsControlPanel.class);
        ViewsControlPanel.Body body = panel.getBody();

        assertTrue(configuration.isEnabled());
        assertEquals("Views", panel.getTitle());
        assertEquals("fa-window-maximize", panel.getIcon());
        assertEquals(25, panel.getOrder());
        assertEquals("Web", panel.getCategory().name());
        assertTrue(body.configuration().viewsEnabled());
        assertEquals("templates/", body.configuration().folder());
        assertEquals(1, body.rendererCount());
        assertTrue(panel.getBadge().contains("routes"));
    }

    @Test
    void discoversStaticAndDynamicViewRoutesWithoutInvokingControllers() {
        InvokedController.invocations.set(0);
        TestViewsRenderer.renderInvocations.set(0);
        ctx = ApplicationContext.run();

        ViewsControlPanel.Body body = ctx.getBean(ViewsControlPanel.class).getBody();
        List<ViewsControlPanel.RouteRow> routes = body.routes();

        assertTrue(routes.stream().anyMatch(route -> "/views/present".equals(route.uri())
            && "present".equals(route.viewName())
            && "FOUND".equals(route.status())));
        assertTrue(routes.stream().anyMatch(route -> "/views/missing".equals(route.uri())
            && "missing".equals(route.viewName())
            && "MISSING_TEMPLATE".equals(route.status())));
        assertTrue(routes.stream().anyMatch(route -> "/views/dynamic".equals(route.uri())
            && route.dynamic()
            && "DYNAMIC".equals(route.status())));
        assertTrue(body.warnings().stream().anyMatch(warning -> warning.title().equals("Missing template")
            && warning.message().contains("/views/missing")));
        assertEquals(0, InvokedController.invocations.get());
        assertEquals(0, TestViewsRenderer.renderInvocations.get());
    }

    @Test
    void reportsNoRendererForStaticViewRoutes() {
        ctx = ApplicationContext.run(Map.of("test.renderer.enabled", false));

        ViewsControlPanel.Body body = ctx.getBean(ViewsControlPanel.class).getBody();

        assertEquals(0, body.rendererCount());
        assertTrue(body.routes().stream().anyMatch(route -> "/views/present".equals(route.uri())
            && "NO_RENDERER".equals(route.status())));
        assertTrue(body.warnings().stream().anyMatch(warning -> warning.title().equals("No Views renderer")));
    }

    @Test
    void reportsViewsDisabledWithoutDisablingThePanel() {
        ctx = ApplicationContext.run(Map.of("micronaut.views.enabled", false));

        ViewsControlPanel.Body body = ctx.getBean(ViewsControlPanel.class).getBody();

        assertFalse(body.configuration().viewsEnabled());
        assertTrue(body.warnings().stream().anyMatch(warning -> warning.title().equals("Views disabled")));
    }

    @Test
    void itCanBeDisabled() {
        ctx = ApplicationContext.run(Map.of(ViewsControlPanel.ENABLED_PROPERTY, false));

        ControlPanelConfiguration configuration = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ViewsControlPanel.NAME));

        assertFalse(configuration.isEnabled());
        assertFalse(ctx.containsBean(ViewsControlPanel.class));
    }

    @Controller("/views")
    static class InvokedController {
        static final AtomicInteger invocations = new AtomicInteger();

        @Get("/present")
        @View("present")
        String present() {
            invocations.incrementAndGet();
            throw new AssertionError("View diagnostics must not invoke controller methods");
        }

        @Get("/missing")
        @View("missing")
        String missing() {
            invocations.incrementAndGet();
            throw new AssertionError("View diagnostics must not invoke controller methods");
        }

        @Get("/dynamic")
        ModelAndView<String> dynamic() {
            invocations.incrementAndGet();
            throw new AssertionError("View diagnostics must not invoke controller methods");
        }
    }

    @Singleton
    @Requires(property = "test.renderer.enabled", notEquals = "false")
    static class TestViewsRenderer implements ViewsRenderer<Object, Object> {
        static final AtomicInteger renderInvocations = new AtomicInteger();

        @Override
        public Writable render(String viewName, Object data, Object request) {
            renderInvocations.incrementAndGet();
            return writer -> {
            };
        }

        @Override
        public boolean exists(String viewName) {
            assertNotNull(viewName);
            return "present".equals(viewName);
        }
    }
}
