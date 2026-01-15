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
package io.micronaut.controlpanel.panels.openapi;

import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.NonNull;

/**
 * Control panel for OpenAPI viewers.
 * Displays a link to an OpenAPI documentation viewer (Swagger UI, Redoc, RapiDoc, Scalar, or OpenAPI Explorer).
 * This panel does not have a details view - it only shows a link in the main dashboard.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@ReflectiveAccess
public class OpenAPIViewerControlPanel extends AbstractControlPanel<OpenAPIViewerControlPanel.Body> {

    /**
     * The name of the OpenAPI control panel category.
     */
    public static final String NAME = "openapi";

    private final String viewerName;
    private final String viewerUrl;

    /**
     * Constructor.
     *
     * @param viewerName the name of the viewer (e.g., "swagger-ui", "redoc")
     * @param viewerUrl the URL to the viewer
     * @param configuration the control panel configuration
     */
    public OpenAPIViewerControlPanel(@NonNull String viewerName,
                                     @NonNull String viewerUrl,
                                     @NonNull ControlPanelConfiguration configuration) {
        super(NAME + "-" + viewerName, configuration);
        this.viewerName = viewerName;
        this.viewerUrl = viewerUrl;
    }

    @Override
    public Body getBody() {
        return new Body(viewerName, viewerUrl);
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "OpenAPI", "fa-file-code");
    }

    @Override
    public String getIcon() {
        return "fa-file-code";
    }

    @Override
    public boolean hasDetails() {
        return false;
    }

    /**
     * Body data for the OpenAPI viewer control panel.
     *
     * @param viewerName the name of the viewer
     * @param viewerUrl the URL to access the viewer
     */
    @ReflectiveAccess
    public record Body(@NonNull String viewerName, @NonNull String viewerUrl) { }
}
