package io.micronaut.controlpanel.ui;

import io.micronaut.controlpanel.core.ConfigurableControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class ControlPanelControllerTest {

    @Inject
    ControlPanelController controller;

    @Inject
    io.micronaut.controlpanel.core.ControlPanelRepository repository;

    @Test
    void testIndexView() {
        var model = (Model) controller.index().body().getModel().get();
        assertEquals(3, model.categories().size());
        assertEquals("test-application", model.applicationName());
        assertEquals(java.util.Set.of("test"), model.activeEnvironments());
        assertEquals(Model.ContentView.INDEX, model.contentView());
        assertEquals(ControlPanel.Category.MAIN, model.ext().get("currentCategory"));
        assertEquals(4, ((java.util.Collection<?>) model.ext().get("controlPanels")).size());
    }

    @Test
    void testByCategoryView() {
        var categoryId = ControlPanel.Category.MAIN.id();
        var model = (Model) controller.byCategory(categoryId).body().getModel().get();
        assertEquals(3, model.categories().size());
        assertEquals("test-application", model.applicationName());
        assertEquals(java.util.Set.of("test"), model.activeEnvironments());
        assertEquals(Model.ContentView.INDEX, model.contentView());
        assertEquals(categoryId, ((ControlPanel.Category) model.ext().get("currentCategory")).id());
        assertEquals(4, ((java.util.Collection<?>) model.ext().get("controlPanels")).size());

        var model2 = (Model) controller.byCategory("application").body().getModel().get();
        assertEquals(1, ((java.util.Collection<?>) model2.ext().get("controlPanels")).size());
    }

    @Test
    void testDetailView() {
        var modelRoutes = (Model) controller.detail("routes").body().getModel().get();
        assertEquals(3, modelRoutes.categories().size());
        assertEquals("test-application", modelRoutes.applicationName());
        assertEquals(java.util.Set.of("test"), modelRoutes.activeEnvironments());
        assertEquals(Model.ContentView.DETAIL, modelRoutes.contentView());
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelRoutes.ext().get("currentCategory")).id());
        assertEquals("routes", ((ControlPanel<?>) modelRoutes.ext().get("controlPanel")).getName());

        var modelEnv = (Model) controller.detail("env").body().getModel().get();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelEnv.ext().get("currentCategory")).id());

        var modelLoggers = (Model) controller.detail("loggers").body().getModel().get();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelLoggers.ext().get("currentCategory")).id());

        var modelHealth = (Model) controller.detail("health").body().getModel().get();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelHealth.ext().get("currentCategory")).id());

        var modelTest = (Model) controller.detail("test").body().getModel().get();
        assertEquals("application", ((ControlPanel.Category) modelTest.ext().get("currentCategory")).id());
    }

    @Singleton
    static class DummyControlPanel implements ControlPanel<String> {

        @Override
        public String getIcon() {
            return io.micronaut.controlpanel.core.config.ControlPanelConfiguration.DEFAULT_ICON;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String getTitle() {
            return "Test Control Panel";
        }

        @Override
        public String getBody() {
            return "Test Control Panel Body";
        }

        @Override
        public Category getCategory() {
            return new Category("application", "My Application", "fa-copy", Integer.MAX_VALUE);
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
