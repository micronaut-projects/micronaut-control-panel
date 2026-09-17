package example

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MyApplicationControlPanelSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run()

    void "it is configured correctly"() {
        when: "retrieve the panel and its configuration"
        MyApplicationControlPanel panel = ctx.getBean(MyApplicationControlPanel)
        ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(panel.name))

        then: "configuration is enabled"
        cfg.enabled

        and: "panel properties match configuration/application.yml"
        panel.title == "My Application Control Panel"
        panel.icon == "fa-plug"
        panel.order == 10

        and: "body content matches implementation"
        panel.body.text() == "This is an application-provided control panel. This text is coming from the body."
    }
}
