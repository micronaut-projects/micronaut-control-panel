package example;

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

//tag::class[]
@Singleton
public class MyApplicationControlPanel extends AbstractControlPanel<String> { // <1>

    private static final String NAME = "my-application"; // <2>

    public MyApplicationControlPanel(@Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration); // <3>
    }

    @Override
    public String getBody() {
        return "This is an application-provided control panel. This text is coming from the body.";
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "My Application", "fa-copy"); // <4>
    }
}
//end::class[]
