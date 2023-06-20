package io.micronaut.controlpanel.core.config

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.ControlPanel
import spock.lang.Specification

class ControlPanelConfigurationSpec extends Specification {

    void "it is enabled by default"() {
        given:
        def ctx = ApplicationContext.run()

        expect:
        ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.run([(ControlPanelConfiguration.PREFIX + ".enabled"): false])

        expect:
        !ctx.getBeansOfType(ControlPanel)

        cleanup:
        ctx.stop()
    }

}
