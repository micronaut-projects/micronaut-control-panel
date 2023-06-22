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

import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.http.annotation.Get;

/**
 * HTTP API for the control panel UI.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
public interface ControlPanelApi {

    String PATH = "${" + ControlPanelModuleConfiguration.PROPERTY_PATH + ":" + ControlPanelModuleConfiguration.DEFAULT_PATH + "}";

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
