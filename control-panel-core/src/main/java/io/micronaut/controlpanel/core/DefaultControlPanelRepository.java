package io.micronaut.controlpanel.core;

import jakarta.inject.Singleton;

import java.util.List;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class DefaultControlPanelRepository implements ControlPanelRepository {

    private final List<ControlPanel> controlPanels;
    private final List<ControlPanel.Category> categories;

    public DefaultControlPanelRepository(List<ControlPanel> controlPanels) {
        this.controlPanels = controlPanels;
        this.categories = controlPanels.stream()
                .map(ControlPanel::getCategory)
                .toList();
    }

    @Override
    public List<ControlPanel> findAll() {
        return controlPanels;
    }

    @Override
    public List<ControlPanel.Category> findAllCategories() {
        return categories;
    }
}
