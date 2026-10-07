from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.controlpanel.core import ControlPanel
from micronaut.controlpanel.core.config import ControlPanelConfiguration
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.MyApplicationControlPanel import MyApplicationControlPanel


@MicronautTest
class MyApplicationControlPanelTest:
    ctx: Annotated[ApplicationContext, Inject]
    panel: Annotated[MyApplicationControlPanel, Inject]

    @Test
    def it_is_configured_correctly(self) -> None:
        # When: retrieve the panel and its configuration
        cfg = self.ctx.getBean(ControlPanelConfiguration, Qualifiers.byName(self.panel.getName()))

        # Then: configuration is enabled
        assert cfg.isEnabled(), "Control panel should be enabled"

        # And: panel properties match configuration/application.yml
        assert self.panel.getTitle() == "My Application Control Panel"
        assert self.panel.getIcon() == "fa-plug"
        assert self.panel.getOrder() == 10

        # And: body content matches implementation
        assert self.panel.getBody().text == "This is an application-provided control panel. This text is coming from the body."

        # And: the Java view of the panel (as the Control Panel UI sees it) exposes the overridden order and category
        java_panel = next(p for p in self.ctx.getBeansOfType(ControlPanel) if p.getName() == "my-application")
        assert java_panel.getOrder() == 10
        assert java_panel.getCategory().id() == "my-application"
        assert java_panel.getCategory().name() == "My Application"
