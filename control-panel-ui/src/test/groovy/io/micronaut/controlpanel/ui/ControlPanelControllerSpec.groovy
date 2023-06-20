package io.micronaut.controlpanel.ui

import io.micronaut.controlpanel.core.ControlPanel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class ControlPanelControllerSpec extends Specification {

    @Inject
    ControlPanelController controller

    void "test the index view"() {
        when:
        def model = controller.index()

        then:
        model.categories().size() == 2
        model.applicationName() == "test-application"
        model.activeEnvironments() == ["test"] as Set
        model.contentView() == Model.ContentView.INDEX
        model.ext()["currentCategory"] == ControlPanel.Category.MAIN
        model.ext()["controlPanels"].size() == 5
    }

    void "test the by category view"(String categoryId, int expectedControlPanels) {
        when:
        def model = controller.byCategory(categoryId)

        then:
        model.categories().size() == 2
        model.applicationName() == "test-application"
        model.activeEnvironments() == ["test"] as Set
        model.contentView() == Model.ContentView.INDEX
        model.ext()["currentCategory"].id() == categoryId
        model.ext()["controlPanels"].size() == expectedControlPanels

        where:
        categoryId                          || expectedControlPanels
        ControlPanel.Category.MAIN.id()     || 5
        "application"                       || 1
    }

    void "test the detail view"(String controlPanelName, String expectedCurrentCategoryId) {
        when:
        def model = controller.detail(controlPanelName)

        then:
        model.categories().size() == 2
        model.applicationName() == "test-application"
        model.activeEnvironments() == ["test"] as Set
        model.contentView() == Model.ContentView.DETAIL
        model.ext()["currentCategory"].id() == expectedCurrentCategoryId
        model.ext()["controlPanel"].name == controlPanelName

        where:
        controlPanelName                     || expectedCurrentCategoryId
        "routes"                             || ControlPanel.Category.MAIN.id()
        "beans"                              || ControlPanel.Category.MAIN.id()
        "loggers"                            || ControlPanel.Category.MAIN.id()
        "health"                             || ControlPanel.Category.MAIN.id()
        "loggers"                            || ControlPanel.Category.MAIN.id()
        "test"                               || "application"
    }

    @Singleton
    static class DummyControlPanel implements ControlPanel<String> {
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
