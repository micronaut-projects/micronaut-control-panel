package example

import io.micronaut.context.ApplicationContext
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MyApplicationControlPanelTest {

    @Test
    fun itIsConfiguredCorrectly() {
        ApplicationContext.run().use { ctx ->
            // When: retrieve the panel and its configuration
            val panel = ctx.getBean(MyApplicationControlPanel::class.java)
            val cfg = ctx.getBean(ControlPanelConfiguration::class.java, Qualifiers.byName(panel.name))

            // Then: configuration is enabled
            assertTrue(cfg.isEnabled, "Control panel should be enabled")

            // And: panel properties match configuration/application.yml
            assertEquals("My Application Control Panel", panel.title)
            assertEquals("fa-plug", panel.icon)
            assertEquals(10, panel.order)

            // And: body content matches implementation
            assertEquals("This is an application-provided control panel. This text is coming from the body.", panel.body.text)
        }
    }
}
