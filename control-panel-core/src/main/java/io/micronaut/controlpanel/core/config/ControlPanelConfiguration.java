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
package io.micronaut.controlpanel.core.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.ConfigurableControlPanel;
import io.micronaut.core.annotation.NonNull;

/**
 * Per-panel configuration properties.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@EachProperty(ControlPanelConfiguration.PREFIX)
public class ControlPanelConfiguration implements ConfigurableControlPanel {
    public static final String DEFAULT_ICON = "fa-cog";
    public static final boolean DEFAULT_ENABLED = true;

    public static final String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".panels";

    private String name;
    private boolean enabled = DEFAULT_ENABLED;
    private int order;
    private String title;
    private String icon = DEFAULT_ICON;

    public ControlPanelConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * Enables/disables this control panel. Default value: {@value #DEFAULT_ENABLED}.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the order of this control panel, since they will be displayed sorted by order.
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * The title is displayed in the header of the card UI element.
     *
     * @return the title of the control panel.
     */
    @Override
    public String getTitle() {
        return title;
    }

    /**
     * Icon class of the card UI element.
     *
     * @return the icon class of the control panel.
     */
    @Override
    public String getIcon() {
        return icon;
    }

    /**
     * The unique name of the control panel. Can be used in URLs.
     */
    @Override
    public @NonNull String getName() {
        return name;
    }

    /**
     * Sets the unique name of the control panel. Can be used in URLs.
     *
     * @param name the name of the control panel.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets whether this control panel is enabled or not.
     *
     * @param enabled whether this control panel is enabled or not.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the order of this control panel, since they will be displayed sorted by order.
     *
     * @param order the order of this control panel.
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * Sets the title of this control panel.
     *
     * @param title the title of this control panel.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Sets the icon of this control panel.
     *
     * @param icon the icon of this control panel.
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }
}
