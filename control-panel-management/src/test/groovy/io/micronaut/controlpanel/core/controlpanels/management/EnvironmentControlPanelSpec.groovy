package io.micronaut.controlpanel.core.controlpanels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification


class EnvironmentControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run(["endpoints.env.enabled": true])

        when:
        def environmentControlPanel = ctx.getBean(EnvironmentControlPanel)

        then:
        environmentControlPanel.body.activeEnvironments == [Environment.TEST] as Set
        (environmentControlPanel.body.packages as List) == [EnvironmentControlPanel.class.packageName]
        environmentControlPanel.body.propertySources.size() == 3 //env, system, context

        cleanup:
        ctx.stop()
    }

}
