package example

import io.micronaut.controlpanel.core.AbstractControlPanel
import io.micronaut.controlpanel.core.ControlPanel
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Named
import jakarta.inject.Singleton

//tag::class[]
@Singleton
class MyApplicationControlPanel(@Named(NAME) configuration: ControlPanelConfiguration) :
    AbstractControlPanel<MyApplicationControlPanel.Body>(NAME, configuration) { // <1> <3>

    companion object {
        const val NAME = "my-application" // <2>
    }

    override fun getBody() = Body("This is an application-provided control panel. This text is coming from the body.")

    override fun getCategory() = ControlPanel.Category(NAME, "My Application", "fas fa-copy", 1) // <4>

    @ReflectiveAccess // <5>
    data class Body(val text: String)
}
//end::class[]
