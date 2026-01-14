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
    }

    @Controller
    static class DummyController {
        @Get
        Object index() { return null; }
    }
}
