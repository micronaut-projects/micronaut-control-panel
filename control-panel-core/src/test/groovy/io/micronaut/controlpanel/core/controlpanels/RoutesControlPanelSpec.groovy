package io.micronaut.controlpanel.core.controlpanels

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class RoutesControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(RoutesControlPanel.NAME))

        then:
        cfg.enabled

        when:
        def panel = ctx.getBean(RoutesControlPanel)

        then:
        panel.title == "HTTP Routes"
        panel.icon == "fa-route"
        panel.order == 20
        panel.body.appRoutes().size() == 0

        panel.body.micronautRoutes().size() > 0
        panel.badge.toInteger() > 0

        cleanup:
        ctx.stop()
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.run([(RoutesControlPanel.ENABLED_PROPERTY): false])

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(RoutesControlPanel.NAME))

        then:
        !cfg.enabled
        !ctx.containsBean(RoutesControlPanel)

        cleanup:
        ctx.stop()
    }

    @Controller
    static class DummyController {

        @Get
        def index() {}
    }
}
