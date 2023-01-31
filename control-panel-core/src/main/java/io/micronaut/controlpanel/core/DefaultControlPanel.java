package io.micronaut.controlpanel.core;

import jakarta.inject.Singleton;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class DefaultControlPanel implements ControlPanel {

    @Override
    public String getDescription() {
        return "Hello World";
    }

    @Override
    public String getName() {
        return "helloworld";
    }
}
