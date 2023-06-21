package example;

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class MyApplicationControlPanel extends AbstractControlPanel<String> {

    private static final String NAME = "my-application";

    public MyApplicationControlPanel(@Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
    }

    @Override
    public String getBody() {
        return "This is an application-provided control panel. This text is coming from the body.";
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "My Application", "fa-copy");
    }
}
