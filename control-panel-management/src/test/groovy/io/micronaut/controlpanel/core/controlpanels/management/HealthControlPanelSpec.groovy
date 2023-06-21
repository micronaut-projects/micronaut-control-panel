package io.micronaut.controlpanel.core.controlpanels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.health.HealthStatus
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicator
import spock.lang.Specification

class HealthControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run([(ServiceReadyHealthIndicator.ENABLED): false])

        when:
        def panel = ctx.getBean(HealthControlPanel)

        then:
        panel.title == "Application Health"
        panel.icon == "fa-laptop-medical"
        panel.order == 0
        panel.body.status == HealthStatus.UP

        cleanup:
        ctx.stop()
    }

    void "it can be disabled"() {
        given:
        def ctx = ApplicationContext.run([(HealthControlPanel.ENABLED_PROPERTY): false])

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(HealthControlPanel.NAME))

        then:
        !cfg.enabled
        !ctx.containsBean(HealthControlPanel)

        cleanup:
        ctx.stop()
    }
}
