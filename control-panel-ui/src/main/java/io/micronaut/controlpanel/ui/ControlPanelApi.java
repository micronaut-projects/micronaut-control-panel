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
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import org.jspecify.annotations.Nullable;

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
     * @param request the current HTTP request
     * @return the model
     */
    @Get
    HttpResponse<String> index(HttpRequest<?> request);

    /**
     * Renders the category view.
     *
     * @param categoryId the category id.
     * @param request the current HTTP request
     * @return the model
     */
    @Get("/categories/{categoryId}")
    HttpResponse<String> byCategory(String categoryId, HttpRequest<?> request);

    /**
     * Renders the control panel detailed view.
     *
     * @param controlPanelName the control panel name.
     * @param request the current HTTP request
     *
     * @return the model
     */
    @Get("/{controlPanelName}")
    HttpResponse<String> detail(String controlPanelName, HttpRequest<?> request);

    /**
     * Refreshes the host application through a control-panel-owned write route.
     *
     * @param request the optional refresh request
     * @return the refresh result
     */
    @Post(ControlPanelSecurityPaths.APPLICATION_PATH + "/refresh")
    HttpResponse<Object> refresh(@Nullable @Body RefreshRequest request);

    /**
     * Stops the host application through a control-panel-owned write route.
     *
     * @return the stop result
     */
    @Post(ControlPanelSecurityPaths.APPLICATION_PATH + "/stop")
    HttpResponse<Object> stop();

    /**
     * @param force whether refresh should run regardless of environment changes
     */
    @Introspected
    record RefreshRequest(boolean force) {
    }
}
