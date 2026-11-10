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
import io.micronaut.controlpanel.ui.ControlPanelRenderer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the panel templates with the Control Panel Handlebars instance, so that a renamed accessor or a template
 * error fails the build instead of the page.
 */
class ViewsTemplatesTest {

    private static ApplicationContext context;
    private static ControlPanelRenderer renderer;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run();
        renderer = context.getBean(ControlPanelRenderer.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void rendersRoutesTemplatesAndTheContentSecurityPolicy() {
        var info = new ViewsInfo(
            true,
            "views",
            true,
            new ViewsInfo.Csp(false, true, "/**", "default-src 'self' <script>"),
            List.of(new ViewsInfo.Renderer("io.micronaut.views.thymeleaf.ThymeleafViewsRenderer", List.of("text/html"))),
            List.of("io.micronaut.views.model.security.SecurityViewModelProcessor"),
            List.of(
                new ViewsInfo.ViewRoute("GET", "/shop", "shop/home", "ShopController.home()", "found by thymeleaf"),
                new ViewsInfo.ViewRoute("GET", "/shop/cart", "shop/cart", "ShopController.cart()", ViewsInfo.ViewRoute.MISSING),
                new ViewsInfo.ViewRoute("GET", "/shop/broken", "shop/broken", "ShopController.broken()", ViewsInfo.ViewRoute.CHECK_FAILED),
                new ViewsInfo.ViewRoute("GET", "/shop/dynamic", null, "ShopController.dynamic()", null)
            ),
            true
        );

        String body = render("body", info);
        assertTrue(body.contains("4 view routes, templates in <code>views</code>."), body);
        assertTrue(body.contains("ThymeleafViewsRenderer"), body);
        assertFalse(body.contains("views disabled"), body);

        String detail = render("detail", info);
        assertTrue(detail.contains("The template of 1 of the routes below was not found."), detail);
        assertTrue(detail.contains("<code class=\"cp-table-code\">GET /shop/cart</code>"), detail);
        assertTrue(detail.contains("<span class=\"badge badge-destructive\">missing</span>"), detail);
        assertTrue(detail.contains("<span class=\"badge badge-outline\">check failed</span>"), detail);
        assertTrue(detail.contains("title=\"Found by found by thymeleaf\">found</span>"), detail);
        assertTrue(detail.contains("view chosen by the controller"), detail);
        assertTrue(detail.contains("enforced on <code>/**</code>, with a nonce"), detail);
        assertTrue(detail.contains("default-src &#x27;self&#x27; &lt;script&gt;"), "values are escaped");
        assertTrue(detail.contains("<span class=\"badge badge-secondary\">text/html</span>"), detail);
        assertTrue(detail.contains("SecurityViewModelProcessor"), detail);
    }

    @Test
    void rendersDisabledViewsWithoutRenderers() {
        var info = new ViewsInfo(false, "views", false, null, List.of(), List.of(), List.of(), false);

        String body = render("body", info);
        assertTrue(body.contains("views disabled"), body);
        assertTrue(body.contains("No views renderer is available."), body);

        String detail = render("detail", info);
        assertTrue(detail.contains("Micronaut Views is disabled"), detail);
        assertTrue(detail.contains("No views renderer is available, so no view can be rendered."), detail);
        assertTrue(detail.contains("No route has a <code>@View</code> annotation"), detail);
        assertTrue(detail.contains("Content Security Policy</span> disabled"), detail);
        assertTrue(detail.contains("No bean changes the model before it is rendered."), detail);
    }

    private static String render(String template, ViewsInfo info) {
        return renderer.render("views/views/" + template, Map.of("controlPanel", Map.of("body", info, "detailedBody", info)));
    }
}
