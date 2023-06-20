package io.micronaut.controlpanel.core.controlpanels.management

import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicator
import spock.lang.Specification

class HealthControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run([(ServiceReadyHealthIndicator.ENABLED): false])

        when:
        def panel = ctx.getBean(HealthControlPanel)

        then:
        panel.body.status == HealthStatus.UP

        cleanup:
        ctx.stop()
    }
}
