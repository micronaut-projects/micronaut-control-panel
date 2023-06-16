package io.micronaut.controlpanel.core;

import io.micronaut.context.annotation.DefaultImplementation;

import java.util.List;
import java.util.Optional;

/**
 * It provides the ability to retrieve {@link ControlPanel} instances.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@DefaultImplementation(DefaultControlPanelRepository.class)
public interface ControlPanelRepository {

    /**
     * Returns all the available {@link ControlPanel} instances.
     */
    List<ControlPanel> findAll();

    /**
     * Returns all the available {@link ControlPanel} instances for the given category.
     */
    List<ControlPanel> findAllByCategory(String categoryId);

    /**
     * Returns the {@link ControlPanel} instance with the given name.
     */
    Optional<ControlPanel> findByName(String name);

    /**
     * Returns all the available {@link ControlPanel.Category} instances.
     */
    List<ControlPanel.Category> findAllCategories();

    /**
     * Returns the {@link ControlPanel.Category} instance with the given id.
     */
    Optional<ControlPanel.Category> findCategoryById(String categoryId);

}
