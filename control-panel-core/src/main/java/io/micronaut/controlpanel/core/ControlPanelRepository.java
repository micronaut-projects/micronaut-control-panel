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
     * @return all the available {@link ControlPanel} instances.
     */
    List<ControlPanel> findAll();

    /**
     * @param categoryId the category id
     *
     * @return all the available {@link ControlPanel} instances for the given category.
     */
    List<ControlPanel> findAllByCategory(String categoryId);

    /**
     * @param name the name of the {@link ControlPanel} instance to find.
     *
     * @return the {@link ControlPanel} instance with the given name.
     */
    Optional<ControlPanel> findByName(String name);

    /**
     * @return all the available {@link ControlPanel.Category} instances.
     */
    List<ControlPanel.Category> findAllCategories();

    /**
     * @param categoryId the category id
     *
     * @return the {@link ControlPanel.Category} instance with the given id.
     */
    Optional<ControlPanel.Category> findCategoryById(String categoryId);

}
