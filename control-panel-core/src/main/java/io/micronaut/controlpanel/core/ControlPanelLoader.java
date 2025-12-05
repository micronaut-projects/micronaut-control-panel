/*
 * Copyright 2017-2025 original authors
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

import java.util.List;

/**
 * Interface for loading control panels dynamically.
 * Implementations of this interface are responsible for discovering
 * and instantiating control panels that should be available in the application.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
public interface ControlPanelLoader {

    /**
     * Loads a list of control panels.
     *
     * @param <CP> the type of control panel
     * @return a list of control panels
     */
    <CP extends ControlPanel<?>> List<CP> loadControlPanels();
}
