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
package io.micronaut.controlpanel.core;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.inject.Singleton;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultControlPanelRepositoryTest {

    private static ApplicationContext ctx;

    @BeforeAll
    static void setupContext() {
        ctx = ApplicationContext.run(Map.of("endpoints.all.enabled", true));
    }

    @AfterAll
    static void tearDownContext() {
        if (ctx != null) {
            ctx.stop();
        }
    }

    @Test
    void itCanFindAll() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var panels = repository.findAll();
        assertEquals(8, panels.size());
    }

    @Test
    void itCanFindAllByCategoryMain() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var panels = repository.findAllByCategory(ControlPanel.Category.MAIN.id());
        assertEquals(5, panels.size());
    }

    @Test
    void itCanFindAllByCategoryApplication() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var panels = repository.findAllByCategory("application");
        assertEquals(1, panels.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"routes", "beans", "env", "info", "loggers", "health", "test"})
    void itCanFindOneByNamePresent(String controlPanelName) {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName(controlPanelName).isPresent());
    }

    @Test
    void itCanFindOneByNameNotFound() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertFalse(repository.findByName("not-found").isPresent());
    }

    @Test
    void itDoesNotFindDisabledControlPanels() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertFalse(repository.findByName("disabled-test").isPresent());
    }

    @Test
    void itCanFindAllCategories() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var categories = repository.findAllCategories();
        assertEquals(3, categories.size());
    }

    @Test
    void itCanFindOneCategoryByIdMain() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var category = repository.findCategoryById(ControlPanel.Category.MAIN.id());
        assertTrue(category.isPresent());
    }

    @Test
    void itCanFindOneCategoryByIdApplication() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var category = repository.findCategoryById("application");
        assertTrue(category.isPresent());
    }

    @Test
    void itCanFindOneCategoryByIdNotFound() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var category = repository.findCategoryById("not-found");
        assertFalse(category.isPresent());
    }

    @Test
    void itCanCountByCategoryIdMain() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        long count = repository.countByCategoryId(ControlPanel.Category.MAIN.id());
        assertEquals(5L, count);
    }

    @Test
    void itCanCountByCategoryIdApplication() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        long count = repository.countByCategoryId("application");
        assertEquals(1L, count);
    }

    @Test
    void itCanCountByCategoryIdNonexistent() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        long count = repository.countByCategoryId("nonexistent");
        assertEquals(0L, count);
    }

    @Singleton
    static class DummyControlPanel implements ControlPanel<String> {
        @Override
        public String getIcon() {
            return ControlPanelConfiguration.DEFAULT_ICON;
        }

        @Override
        public boolean isEnabled() {
            return true;
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
            return new Category("application", "My Application", "fas fa-copy");
        }

        @Override
        public String getName() {
            return "test";
        }
    }

    @Singleton
    static class DisabledDummyControlPanel implements ControlPanel<String> {
        @Override
        public String getIcon() {
            return ControlPanelConfiguration.DEFAULT_ICON;
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String getTitle() {
            return "Disabled Test Control Panel";
        }

        @Override
        public String getBody() {
            return "Disabled Test Control Panel Body";
        }

        @Override
        public Category getCategory() {
            return new Category("application", "My Application", "fas fa-copy");
        }

        @Override
        public String getName() {
            return "disabled-test";
        }
    }
}
