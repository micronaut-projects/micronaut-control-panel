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
package io.micronaut.controlpanel.ui;

import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.http.HttpRequest;
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
        var model = controller.byCategoryModel(ControlPanel.Category.MAIN.id(), HttpRequest.GET("/control-panel")).orElseThrow();
        assertEquals(3, model.categories().size());
        assertEquals("test-application", model.applicationName());
        assertEquals(java.util.Set.of("test"), model.activeEnvironments());
        assertEquals(Model.ContentView.INDEX, model.contentView());
        assertEquals(ControlPanel.Category.MAIN, model.ext().get("currentCategory"));
        assertEquals(5, ((java.util.Collection<?>) model.ext().get("controlPanels")).size());
    }

    @Test
    void testByCategoryView() {
        var categoryId = ControlPanel.Category.MAIN.id();
        var model = controller.byCategoryModel(categoryId, HttpRequest.GET("/control-panel/categories/" + categoryId)).orElseThrow();
        assertEquals(3, model.categories().size());
        assertEquals("test-application", model.applicationName());
        assertEquals(java.util.Set.of("test"), model.activeEnvironments());
        assertEquals(Model.ContentView.INDEX, model.contentView());
        assertEquals(categoryId, ((ControlPanel.Category) model.ext().get("currentCategory")).id());
        assertEquals(5, ((java.util.Collection<?>) model.ext().get("controlPanels")).size());

        var model2 = controller.byCategoryModel("application", HttpRequest.GET("/control-panel/categories/application")).orElseThrow();
        assertEquals(1, ((java.util.Collection<?>) model2.ext().get("controlPanels")).size());
    }

    @Test
    void testDetailView() {
        var modelRoutes = controller.detailModel("routes", HttpRequest.GET("/control-panel/routes")).orElseThrow();
        assertEquals(3, modelRoutes.categories().size());
        assertEquals("test-application", modelRoutes.applicationName());
        assertEquals(java.util.Set.of("test"), modelRoutes.activeEnvironments());
        assertEquals(Model.ContentView.DETAIL, modelRoutes.contentView());
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelRoutes.ext().get("currentCategory")).id());
        assertEquals("routes", ((ControlPanel<?>) modelRoutes.ext().get("controlPanel")).getName());

        var modelEnv = controller.detailModel("env", HttpRequest.GET("/control-panel/env")).orElseThrow();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelEnv.ext().get("currentCategory")).id());

        var modelLoggers = controller.detailModel("loggers", HttpRequest.GET("/control-panel/loggers")).orElseThrow();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelLoggers.ext().get("currentCategory")).id());

        var modelHealth = controller.detailModel("health", HttpRequest.GET("/control-panel/health")).orElseThrow();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelHealth.ext().get("currentCategory")).id());

        var modelInfo = controller.detailModel("info", HttpRequest.GET("/control-panel/info")).orElseThrow();
        assertEquals(ControlPanel.Category.MAIN.id(), ((ControlPanel.Category) modelInfo.ext().get("currentCategory")).id());

        var modelTest = controller.detailModel("test", HttpRequest.GET("/control-panel/test")).orElseThrow();
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
            return new Category("application", "My Application", "fas fa-copy", Integer.MAX_VALUE);
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
