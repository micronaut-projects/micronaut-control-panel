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
package io.micronaut.controlpanel.panels.r2dbc;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcBody;
import jakarta.inject.Named;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel for R2DBC connection factory diagnostics.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@EachBean(R2dbcDiagnosticsService.class)
public class R2dbcControlPanel extends AbstractEachBeanControlPanel<R2dbcBody> {

    /**
     * R2DBC panel configuration name.
     */
    public static final String NAME = "r2dbc";
    /**
     * R2DBC panel enablement property.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    /**
     * Default R2DBC panel icon class.
     */
    public static final String DEFAULT_ICON_CLASS = "fas fa-database";

    private final R2dbcDiagnosticsService diagnosticsService;

    /**
     * Creates an R2DBC control panel for one connection factory.
     *
     * @param diagnosticsService the diagnostics service for one connection factory
     * @param configuration the panel configuration
     */
    public R2dbcControlPanel(@Parameter R2dbcDiagnosticsService diagnosticsService,
                             @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    protected String getBeanName() {
        return diagnosticsService.getBeanName();
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public R2dbcBody getBody() {
        return diagnosticsService.getBody();
    }

    @Override
    public String getTitle() {
        return "R2DBC: " + getBeanName();
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getIcon() {
        return DEFAULT_ICON_CLASS;
    }

    @Override
    public String getDetailLinkName() {
        return "Diagnostics";
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "R2DBC", DEFAULT_ICON_CLASS);
    }
}
