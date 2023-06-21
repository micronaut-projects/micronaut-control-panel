package io.micronaut.controlpanel.core.controlpanels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.logging.LogLevel
import spock.lang.Specification

class LoggersControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run(["endpoints.loggers.enabled": true])

        when:
        def panel = ctx.getBean(LoggersControlPanel)

        then:
        panel.title == "Loggers"
        panel.icon == "fa-file-lines"
        panel.order == 40
        panel.body.levels().size() == LogLevel.values().size()
        panel.body.loggers().keySet().size() == 2
        panel.body.loggers()["ROOT"].configuredLevel == LogLevel.INFO
        panel.body.loggers()["io.micronaut.controlpanel"].configuredLevel == LogLevel.DEBUG

        cleanup:
        ctx.stop()
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.run([(LoggersControlPanel.ENABLED_PROPERTY): false])

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(LoggersControlPanel.NAME))

        then:
        !cfg.enabled
        !ctx.containsBean(LoggersControlPanel)

        cleanup:
        ctx.stop()
    }
}
