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
package io.micronaut.controlpanel.ui;


import io.micronaut.controlpanel.core.ControlPanel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Model to be passed to the view for rendering.
 *
 * @param categories The available categories.
 * @param applicationName The application name.
 * @param activeEnvironments The active environments.
 * @param contentView The content view.
 * @param canRefresh Whether the host application can be refreshed.
 * @param canStop Whether the host application can be stopped.
 * @param ext Any additional data to be passed to the view.
 */
public record Model(
    List<ControlPanel.Category> categories,
    String applicationName,
    Set<String> activeEnvironments,
    ContentView contentView,
    boolean canRefresh,
    boolean canStop,
    Map<String, Object> ext) {

    /**
     * Content view to be rendered by the control panel.
     */
    enum ContentView {
        INDEX, DETAIL
    }
}
