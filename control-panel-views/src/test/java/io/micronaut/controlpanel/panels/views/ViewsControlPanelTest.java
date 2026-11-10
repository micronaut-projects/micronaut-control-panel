package io.micronaut.controlpanel.panels.views;

import com.example.shop.ShopController;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewsControlPanelTest {

    private static final String HANDLER = ShopController.class.getName() + "#";

    @Test
    void listsViewRoutesAndChecksTemplatesInTheDetailOnly() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", ShopController.SPEC,
            "micronaut.views.csp.enabled", true,
            "micronaut.views.csp.policy-directives", "default-src 'self'"
        ))) {
            ViewsControlPanel panel = context.getBean(ViewsControlPanel.class);
            assertEquals("Views", panel.getTitle());

            ViewsInfo body = panel.getBody();
            assertTrue(body.enabled());
            assertEquals("views/", body.folder());
            assertFalse(body.templatesChecked());
            assertEquals(List.of(
                new ViewsInfo.ViewRoute("GET", "/shop", "home", HANDLER + "home", null),
                new ViewsInfo.ViewRoute("GET", "/shop/checkout", "checkout", HANDLER + "checkout", null),
                new ViewsInfo.ViewRoute("GET", "/shop/product/{id}", null, HANDLER + "product", null)
            ), body.routes());
            assertTrue(body.renderers().stream().anyMatch(renderer -> renderer.type().contains("Thymeleaf")), body.renderers().toString());
            assertNotNull(body.csp());
            assertEquals("default-src 'self'", body.csp().policyDirectives());

            ViewsInfo detail = panel.getDetailedBody();
            assertTrue(detail.templatesChecked());
            List<ViewsInfo.ViewRoute> routes = detail.routes();
            assertTrue(routes.get(0).template().contains("Thymeleaf"), routes.toString());
            assertTrue(routes.get(1).missing(), routes.toString());
            assertNull(routes.get(2).template());
            assertEquals(1, detail.missingCount());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(ViewsControlPanel.NAME).isPresent());
        }
    }

    @Test
    void reportsDisabledViewsAndCsp() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.views.enabled", false))) {
            ViewsInfo body = context.getBean(ViewsControlPanel.class).getBody();
            assertFalse(body.enabled());
            assertNull(body.csp());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(ViewsControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(ViewsControlPanel.class));
        }
    }
}
