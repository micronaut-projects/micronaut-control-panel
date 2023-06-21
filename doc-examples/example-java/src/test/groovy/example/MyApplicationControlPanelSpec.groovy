package example

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class MyApplicationControlPanelSpec extends Specification {

    void "it is configured correctly"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(MyApplicationControlPanel.NAME))

        then:
        cfg.enabled

        when:
        def panel = ctx.getBean(MyApplicationControlPanel)

        then:
        panel.title == "My Application Control Panel"
        panel.icon == "fa-cog"
        panel.order == 0
        panel.body == "This is an application-provided control panel. This text is coming from the body."

        cleanup:
        ctx.stop()
    }

}
