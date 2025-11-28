/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.core;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link ControlPanelRepository} that gets the control panels injected
 * as beans.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class DefaultControlPanelRepository implements ControlPanelRepository {

    private final Collection<ControlPanel> controlPanels;
    private final List<ControlPanel.Category> categories;

    /**
     * Default constructor.
     *
     * @param controlPanels the control panels available in the application context.
     */
    public DefaultControlPanelRepository(BeanContext beanContext) {
        this.controlPanels = beanContext.getBeansOfType(ControlPanel.class);
        this.categories = controlPanels.stream()
                .map(ControlPanel::getCategory)
                .distinct()
                .sorted(Comparator.comparing(ControlPanel.Category::order).thenComparing(ControlPanel.Category::name))
                .toList();
    }

    @Override
    public List<ControlPanel> findAll() {
        return controlPanels.stream()
            .sorted(Comparator.comparing(ControlPanel::getOrder))
            .toList();
    }

    @Override
    public List<ControlPanel> findAllByCategory(String categoryId) {
        return controlPanels.stream()
                .filter(controlPanel -> controlPanel.getCategory().id().equals(categoryId))
                .sorted(Comparator.comparing(ControlPanel::getOrder))
                .toList();
    }

    @Override
    public Optional<ControlPanel> findByName(String name) {
        return controlPanels.stream()
                .filter(controlPanel -> controlPanel.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<ControlPanel.Category> findAllCategories() {
        return categories;
    }

    @Override
    public Optional<ControlPanel.Category> findCategoryById(String categoryId) {
        return categories.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst();
    }
}
