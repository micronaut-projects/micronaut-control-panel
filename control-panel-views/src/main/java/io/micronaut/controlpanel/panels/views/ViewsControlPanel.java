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
package io.micronaut.controlpanel.panels.views;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.views.ViewsRenderer;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only view of Micronaut Views: its configuration, the renderers, the model processors, and the routes that
 * render a view, with whether each template exists.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = ViewsRenderer.class)
@Requires(property = ViewsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ViewsControlPanel extends AbstractControlPanel<ViewsInfo> {

    public static final String NAME = "views";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final ViewsInspector inspector;

    /**
     * @param inspector the views inspector
     * @param configuration the panel configuration
     */
    public ViewsControlPanel(ViewsInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    /**
     * Does not look up templates, so that rendering the dashboard stays cheap.
     *
     * @return the configuration, renderers and routes, without template checks
     */
    @Override
    public ViewsInfo getBody() {
        return inspector.inspect(false);
    }

    /**
     * @return the configuration, renderers and routes, with whether each template exists, for the detail view
     */
    @ReflectiveAccess
    public ViewsInfo getDetailedBody() {
        return inspector.inspect(true);
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }
}
