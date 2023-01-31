package io.micronaut.controlpanel.core;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Controller("/control-panel")
public class ControlPanelController {

    private List<ControlPanel> controlPanels;

    public ControlPanelController(List<ControlPanel> controlPanels) {
        this.controlPanels = controlPanels;
    }

    @Get
    public List<ControlPanel> findAll() {
        return controlPanels;
    }

}
