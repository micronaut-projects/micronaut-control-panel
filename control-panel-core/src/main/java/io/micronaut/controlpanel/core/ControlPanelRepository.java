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

    List<ControlPanel> findAllByCategory(String categoryId);

    ControlPanel findByName(String name);

    List<ControlPanel.Category> findAllCategories();

    ControlPanel.Category findCategoryById(String categoryId);

}
