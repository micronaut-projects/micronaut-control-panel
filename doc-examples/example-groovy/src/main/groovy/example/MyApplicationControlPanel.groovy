package example

import io.micronaut.controlpanel.core.AbstractControlPanel
import io.micronaut.controlpanel.core.ControlPanel
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Named
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class MyApplicationControlPanel extends AbstractControlPanel<MyApplicationControlPanel.Body> { // <1>

    private static final String NAME = "my-application" // <2>

    MyApplicationControlPanel(@Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration) // <3>
    }

    @Override
    Body getBody() {
        new Body("This is an application-provided control panel. This text is coming from the body.")
    }

    @Override
    ControlPanel.Category getCategory() {
        new ControlPanel.Category(NAME, "My Application", "fas fa-copy", 1) // <4>
    }

    @ReflectiveAccess // <5>
    static record Body(String text) {}
}
//end::class[]
