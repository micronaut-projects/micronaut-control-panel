from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.controlpanel.core import AbstractControlPanel
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
class MyApplicationControlPanel(AbstractControlPanel[Body]):  # <1>

    def __init__(self, configuration: Annotated[ControlPanelConfiguration, Named(NAME)]):
        super().__init__(NAME, configuration)  # <3>

    def getBody(self) -> Body:
        return Body("This is an application-provided control panel. This text is coming from the body.")

    def getCategory(self) -> Category:
        return Category(NAME, "My Application", "fas fa-copy", 1)  # <4>
# end::class[]
