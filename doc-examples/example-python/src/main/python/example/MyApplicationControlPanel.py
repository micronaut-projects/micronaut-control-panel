from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Executable
from micronaut.controlpanel.core import ControlPanel
from micronaut.controlpanel.core.ControlPanel import Category
from micronaut.controlpanel.core.config import ControlPanelConfiguration
from micronaut.core.annotation import ReflectiveAccess

# tag::class[]
NAME: str = "my-application"  # <2>


@ReflectiveAccess  # <5>
@dataclass
class Body:
    text: str


@Singleton
class MyApplicationControlPanel(ControlPanel[Body]):  # <1>

    def __init__(self, configuration: Annotated[ControlPanelConfiguration, Named(NAME)]):
        self.configuration = configuration  # <3>

    def getName(self) -> str:
        return NAME

    def getTitle(self) -> str:
        return self.configuration.getTitle()

    def getIcon(self) -> str:
        return self.configuration.getIcon()

    @Executable  # methods overriding a default method of the Java interface are bridged with @Executable
    def getOrder(self) -> int:
        return self.configuration.getOrder()

    def isEnabled(self) -> bool:
        return self.configuration.isEnabled()

    def getBody(self) -> Body:
        return Body("This is an application-provided control panel. This text is coming from the body.")

    @Executable
    def getCategory(self) -> Category:
        return Category(NAME, "My Application", "fas fa-copy", 1)  # <4>
# end::class[]
