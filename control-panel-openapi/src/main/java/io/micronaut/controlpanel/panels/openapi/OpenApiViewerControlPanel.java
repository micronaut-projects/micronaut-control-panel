/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * A panel representing a single OpenAPI viewer link.
 */
final class OpenApiViewerControlPanel extends AbstractControlPanel<OpenApiViewerControlPanel.Body> {

    private static final Category OPENAPI_CATEGORY = new Category("openapi", "OpenAPI", "fa-book-open", 100);

    private final Body body;

    OpenApiViewerControlPanel(String panelName, String viewer, String href, ControlPanelConfiguration configuration) {
        super(panelName, configuration);
        this.body = new Body(viewer, href);
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public boolean hasDetails() {
        return false;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return OPENAPI_CATEGORY;
    }

    @Override
    public View getBodyView() {
        // single shared template
        return new View("/views/openapi/link");
    }

    @ReflectiveAccess
    record Body(String name, String href) { }
}
