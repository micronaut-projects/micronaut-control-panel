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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;

import java.time.Duration;

/**
 * R2DBC diagnostics panel configuration.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@ConfigurationProperties(R2dbcPanelConfiguration.PREFIX)
public class R2dbcPanelConfiguration {

    /**
     * R2DBC panel configuration prefix.
     */
    public static final String PREFIX = ControlPanelConfiguration.PREFIX + "." + R2dbcControlPanel.NAME;
    /**
     * Default timeout for R2DBC health probes.
     */
    public static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private Duration healthTimeout = DEFAULT_HEALTH_TIMEOUT;
    private boolean healthEnabled = true;
    private boolean showOptionValues;

    /**
     * Creates the default R2DBC panel configuration.
     */
    public R2dbcPanelConfiguration() {
    }

    /**
     * Timeout for a validation query executed while rendering the panel.
     *
     * @return the timeout
     */
    public Duration getHealthTimeout() {
        return healthTimeout;
    }

    /**
     * Sets the timeout for a validation query executed while rendering the panel.
     *
     * @param healthTimeout the timeout
     */
    public void setHealthTimeout(Duration healthTimeout) {
        this.healthTimeout = healthTimeout;
    }

    /**
     * Whether the panel should run R2DBC health validation queries.
     *
     * @return true when enabled
     */
    public boolean isHealthEnabled() {
        return healthEnabled;
    }

    /**
     * Sets whether the panel should run R2DBC health validation queries.
     *
     * @param healthEnabled true when enabled
     */
    public void setHealthEnabled(boolean healthEnabled) {
        this.healthEnabled = healthEnabled;
    }

    /**
     * Whether safe allow-listed option values should be rendered.
     *
     * @return true when values are shown
     */
    public boolean isShowOptionValues() {
        return showOptionValues;
    }

    /**
     * Sets whether safe allow-listed option values should be rendered.
     *
     * @param showOptionValues true when values are shown
     */
    public void setShowOptionValues(boolean showOptionValues) {
        this.showOptionValues = showOptionValues;
    }
}
