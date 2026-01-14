package io.micronaut.controlpanel.core;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.inject.Singleton;

import static org.junit.jupiter.api.Assertions.*;

class DefaultControlPanelRepositoryTest {

    private static ApplicationContext ctx;

    @BeforeAll
    static void setupContext() {
        ctx = ApplicationContext.run(java.util.Map.of("endpoints.all.enabled", true));
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
        assertEquals(7, panels.size());
    }

    @Test
    void itCanFindAllByCategoryMain() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var panels = repository.findAllByCategory(ControlPanel.Category.MAIN.id());
        assertEquals(4, panels.size());
    }

    @Test
    void itCanFindAllByCategoryApplication() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        var panels = repository.findAllByCategory("application");
        assertEquals(1, panels.size());
    }

    @Test
    void itCanFindOneByNameRoutes() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName("routes").isPresent());
    }

    @Test
    void itCanFindOneByNameBeans() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName("beans").isPresent());
    }

    @Test
    void itCanFindOneByNameEnv() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName("env").isPresent());
    }

    @Test
    void itCanFindOneByNameLoggers() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName("loggers").isPresent());
    }

    @Test
    void itCanFindOneByNameHealth() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName("health").isPresent());
    }

    @Test
    void itCanFindOneByNameTest() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertTrue(repository.findByName("test").isPresent());
    }

    @Test
    void itCanFindOneByNameNotFound() {
        ControlPanelRepository repository = ctx.getBean(ControlPanelRepository.class);
        assertFalse(repository.findByName("not-found").isPresent());
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
        assertEquals(4L, count);
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
            return new Category("application", "My Application", "fa-copy");
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
