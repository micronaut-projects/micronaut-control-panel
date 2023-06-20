package io.micronaut.controlpanel.core.controlpanels

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import spock.lang.Specification

class RoutesControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def routesControlPanel = ctx.getBean(RoutesControlPanel)

        then:
        routesControlPanel.body.appRoutes().size() == 0
        routesControlPanel.body.micronautRoutes().size() == 1
        routesControlPanel.badge == "2" //GET and HEAD
    }

    @Controller
    static class DummyController {

        @Get
        def index() {}
    }
}
