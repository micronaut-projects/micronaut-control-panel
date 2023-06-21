package io.micronaut.controlpanel.panels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification


class EnvironmentControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run(["endpoints.env.enabled": true])

        when:
        def panel = ctx.getBean(EnvironmentControlPanel)

        then:
        panel.title == "Environment Properties"
        panel.icon == "fa-sliders"
        panel.order == 10
        panel.body.activeEnvironments == [Environment.TEST] as Set
        (panel.body.packages as List) == [EnvironmentControlPanel.class.packageName]
        panel.body.propertySources.size() == 4 //env, system, context and control-panel

        cleanup:
        ctx.stop()
    }

}
