package io.micronaut.controlpanel.core.controlpanels.management

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class BeansControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def beansControlPanel = ctx.getBean(BeansControlPanel)

        then:
        beansControlPanel.body.micronautBeansByPackage()
        beansControlPanel.body.otherBeansByPackage()

        cleanup:
        ctx.stop()
    }

}
