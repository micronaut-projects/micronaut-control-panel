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
package io.micronaut.controlpanel.panels.servlet;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContext;

/**
 * Control panel that displays read-only Servlet runtime metadata.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@Singleton
@Requires(classes = ServletContext.class)
@Requires(property = ServletControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ServletControlPanel extends AbstractControlPanel<ServletRuntimeBody> {

    /**
     * Stable panel name.
     */
    public static final String NAME = "servlet-runtime";
    /**
     * Configuration property that enables the panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    /**
     * Runtime category for Servlet diagnostics.
     */
    public static final Category CATEGORY = new Category("runtime", "Runtime", "fa-server");

    private final ServletRuntimeService runtimeService;

    /**
     * Constructor.
     *
     * @param runtimeService servlet runtime service
     * @param configuration panel configuration
     */
    public ServletControlPanel(ServletRuntimeService runtimeService,
                                @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.runtimeService = runtimeService;
    }

    @Override
    public ServletRuntimeBody getBody() {
        return runtimeService.getBody();
    }

    @Override
    public String getBadge() {
        return runtimeService.getBody().runtimeName();
    }

    @Override
    public Category getCategory() {
        return CATEGORY;
    }
}
