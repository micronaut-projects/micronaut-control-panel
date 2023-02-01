package io.micronaut.controlpanel.core;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Introspected
public class SampleControlPanel implements ControlPanel {

    @Override
    public String getName() {
        return "sample";
    }

    @Override
    public String getTitle() {
        return "Sample Control Panel";
    }

    @Override
    public String getBody() {
        return "This is a sample control panel";
    }

    @Override
    public Map<String, Object> getModel() {
        return Map.of(
            "text", "Text for the detailed view of the control panel"
        );
    }

    @Override
    public View getView() {
        return View.CARD;
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SampleControlPanel{");
        sb.append("name='").append(getName()).append('\'');
        sb.append(", title='").append(getTitle()).append('\'');
        sb.append(", body='").append(getBody()).append('\'');
        sb.append(", model=").append(getModel());
        sb.append(", view=").append(getView());
        sb.append(", category=").append(getCategory());
        sb.append(", detailedView=").append(getDetailedView());
        sb.append(", order=").append(getOrder());
        sb.append(", enabled=").append(isEnabled());
        sb.append('}');
        return sb.toString();
    }
}
