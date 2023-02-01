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
    public String getTitle() {
        return "Sample Control Panel";
    }

    @Override
    public String getBody() {
        return "This is a sample control panel";
    }

    @Override
    public Map<String, Object> getModel() {
        return Collections.emptyMap();
    }

    @Override
    public View getView() {
        return View.CARD;
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }
}
