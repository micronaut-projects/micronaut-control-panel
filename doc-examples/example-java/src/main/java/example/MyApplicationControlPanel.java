package example;

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

//tag::class[]
@Singleton
public class MyApplicationControlPanel extends AbstractControlPanel<MyApplicationControlPanel.Body> { // <1>

    private static final String NAME = "my-application"; // <2>

    public MyApplicationControlPanel(@Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration); // <3>
    }

    @Override
    public Body getBody() {
        return new Body("This is an application-provided control panel. This text is coming from the body.");
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "My Application", "fas fa-copy", 1); // <4>
    }

    @ReflectiveAccess // <5>
    public record Body(String text){}
}
//end::class[]
