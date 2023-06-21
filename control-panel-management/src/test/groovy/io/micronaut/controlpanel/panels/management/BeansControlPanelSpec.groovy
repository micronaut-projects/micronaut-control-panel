package io.micronaut.controlpanel.panels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class BeansControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def panel = ctx.getBean(BeansControlPanel)

        then:
        panel.title == "Bean Definitions"
        panel.icon == "fa-plug"
        panel.order == 30
        panel.body.micronautBeansByPackage()
        panel.body.otherBeansByPackage()

        cleanup:
        ctx.stop()
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.run([(BeansControlPanel.ENABLED_PROPERTY): false])

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(BeansControlPanel.NAME))

        then:
        !cfg.enabled
        !ctx.containsBean(BeansControlPanel)

        cleanup:
        ctx.stop()
    }

}
