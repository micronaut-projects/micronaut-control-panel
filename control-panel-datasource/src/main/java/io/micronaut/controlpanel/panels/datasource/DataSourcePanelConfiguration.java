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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/**
 * The control panel settings of an individual datasource, configured under
 * {@code micronaut.control-panel.panels.datasource.datasources.<name>}, where the name is the datasource name.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@EachProperty(DataSourceControlPanel.DATASOURCES_PREFIX)
public final class DataSourcePanelConfiguration {

    /**
     * The default value of {@link #isEnabled()}.
     */
    public static final boolean DEFAULT_ENABLED = true;

    private final String name;
    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @param name the datasource name
     */
    public DataSourcePanelConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return the datasource name
     */
    public String getName() {
        return name;
    }

    /**
     * @return whether the datasource is shown in the control panel
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether the datasource is shown in the control panel. A hidden datasource is never inspected: no metadata query is
     * run against it, its connection settings are not shown, and its schema, table, pool and query routes respond with
     * {@code 404 Not Found}. Default value: {@value #DEFAULT_ENABLED}.
     *
     * @param enabled whether the datasource is shown
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
