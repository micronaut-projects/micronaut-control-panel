package io.micronaut.controlpanel.core;

import java.util.List;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanelRepository {

    List<ControlPanel> findAll();

    List<ControlPanel.Category> findAllCategories();

}
