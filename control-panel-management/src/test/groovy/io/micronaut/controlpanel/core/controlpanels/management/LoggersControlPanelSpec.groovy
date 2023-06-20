package io.micronaut.controlpanel.core.controlpanels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.logging.LogLevel
import spock.lang.Specification

class LoggersControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run(["endpoints.loggers.enabled": true])

        when:
        def panel = ctx.getBean(LoggersControlPanel)

        then:
        panel.body.levels().size() == LogLevel.values().size()
        panel.body.loggers().keySet().size() == 2
        panel.body.loggers()["ROOT"].configuredLevel == LogLevel.INFO
        panel.body.loggers()["io.micronaut.controlpanel"].configuredLevel == LogLevel.DEBUG

        cleanup:
        ctx.stop()
    }
}
