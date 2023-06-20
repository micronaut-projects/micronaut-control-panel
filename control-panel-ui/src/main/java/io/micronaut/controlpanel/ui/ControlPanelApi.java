package io.micronaut.controlpanel.ui;

import io.micronaut.http.annotation.Get;

/**
 * HTTP API for the control panel UI.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanelApi {

    //TODO make configurable
    String PATH = "/control-panel";

    /**
     * Renders the index view.
     *
     * @return the model
     */
    @Get
    Model index();

    /**
     * Renders the category view.
     *
     * @param categoryId the category id.
     *
     * @return the model
     */
    @Get("/categories/{categoryId}")
    Model byCategory(String categoryId);

    /**
     * Renders the control panel detailed view.
     *
     * @param controlPanelName the control panel name.
     *
     * @return the model
     */
    @Get("/{controlPanelName}")
    Model detail(String controlPanelName);
}
