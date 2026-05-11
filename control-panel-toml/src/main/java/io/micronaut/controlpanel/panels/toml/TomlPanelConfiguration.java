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
package io.micronaut.controlpanel.panels.toml;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;

/**
 * Configuration for the TOML configuration diagnostics panel.
 */
@ConfigurationProperties(TomlPanelConfiguration.PREFIX)
public class TomlPanelConfiguration {

    /**
     * Configuration prefix for TOML panel options.
     */
    public static final String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".toml";
    /**
     * Configuration property that enables the empty diagnostics state.
     */
    public static final String SHOW_EMPTY_PROPERTY = PREFIX + ".show-empty";

    private boolean showValues;
    private boolean showOverriddenValues;
    private boolean showEmpty;

    /**
     * Creates default TOML panel configuration.
     */
    public TomlPanelConfiguration() {
    }

    /**
     * Whether non-sensitive effective TOML values should be displayed.
     *
     * @return true when values may be displayed
     */
    public boolean isShowValues() {
        return showValues;
    }

    /**
     * Sets whether non-sensitive effective TOML values should be displayed.
     *
     * @param showValues Whether non-sensitive effective TOML values should be displayed.
     */
    public void setShowValues(boolean showValues) {
        this.showValues = showValues;
    }

    /**
     * Whether non-sensitive overridden TOML values should be displayed.
     *
     * @return true when overridden values may be displayed
     */
    public boolean isShowOverriddenValues() {
        return showOverriddenValues;
    }

    /**
     * Sets whether non-sensitive overridden TOML values should be displayed.
     *
     * @param showOverriddenValues Whether non-sensitive overridden TOML values should be displayed.
     */
    public void setShowOverriddenValues(boolean showOverriddenValues) {
        this.showOverriddenValues = showOverriddenValues;
    }

    /**
     * Whether the panel should appear when TOML support is present but no TOML property sources are loaded.
     *
     * @return true when empty diagnostics are enabled
     */
    public boolean isShowEmpty() {
        return showEmpty;
    }

    /**
     * Sets whether the panel should appear when no TOML property sources are loaded.
     *
     * @param showEmpty Whether the panel should appear when no TOML property sources are loaded.
     */
    public void setShowEmpty(boolean showEmpty) {
        this.showEmpty = showEmpty;
    }
}
