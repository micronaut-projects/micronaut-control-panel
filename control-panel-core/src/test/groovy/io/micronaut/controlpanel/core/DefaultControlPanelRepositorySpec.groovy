package io.micronaut.controlpanel.core

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class DefaultControlPanelRepositorySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(["endpoints.all.enabled": true])

    void "it can find all"() {
        given:
        def repository = ctx.getBean(ControlPanelRepository)

        when:
        def panels = repository.findAll()

        then:
        panels.size() == 6
    }

    void "it can find all by category"(String categoryId, int expectedPanels) {
        given:
        def repository = ctx.getBean(ControlPanelRepository)

        when:
        def panels = repository.findAllByCategory(categoryId)

        then:
        panels.size() == expectedPanels

        where:
        categoryId                      || expectedPanels
        ControlPanel.Category.MAIN.id() || 5
        "application"                   || 1
    }

    void "it can find one by name"(String controlPanelName, boolean expectedIsPresent) {
        given:
        def repository = ctx.getBean(ControlPanelRepository)

        when:
        def panel = repository.findByName(controlPanelName)

        then:
        panel.isPresent() == expectedIsPresent

        where:
        controlPanelName    || expectedIsPresent
        "routes"            || true
        "beans"             || true
        "env"               || true
        "loggers"           || true
        "health"            || true
        "test"              || true
        "not-found"         || false
    }

    void "it can find all categories"() {
        given:
        def repository = ctx.getBean(ControlPanelRepository)

        when:
        def categories = repository.findAllCategories()

        then:
        categories.size() == 2
    }

    void "it can find one category by id"(String categoryId, boolean expectedIsPresent) {
        given:
        def repository = ctx.getBean(ControlPanelRepository)

        when:
        def category = repository.findCategoryById(categoryId)

        then:
        category.isPresent() == expectedIsPresent

        where:
        categoryId                      || expectedIsPresent
        ControlPanel.Category.MAIN.id() || true
        "application"                   || true
        "not-found"                     || false
    }

    @Singleton
    static class DummyControlPanel implements ControlPanel<String> {

        @Override
        String getIcon() {
            return ControlPanelConfiguration.DEFAULT_ICON
        }

        @Override
        boolean isEnabled() {
            return true
        }

        @Override
        String getTitle() {
            return "Test Control Panel"
        }

        @Override
        String getBody() {
            return "Test Control Panel Body"
        }

        @Override
        Category getCategory() {
            return new Category("application", "My Application", "fa-copy")
        }

        @Override
        String getName() {
            return "test"
        }
    }
}
