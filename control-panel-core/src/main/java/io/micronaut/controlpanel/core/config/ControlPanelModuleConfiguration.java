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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.Toggleable;

import java.util.Set;

/**
 * Configuration properties for the control panel module.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@ConfigurationProperties(ControlPanelModuleConfiguration.PREFIX)
public class ControlPanelModuleConfiguration implements Toggleable {

    public static final boolean DEFAULT_ENABLED = true;
    public static final Set<String> DEFAULT_ALLOWED_ENVIRONMENTS = Set.of(Environment.DEVELOPMENT, Environment.TEST);

    public static final String PREFIX = "micronaut.control-panel";
    public static final String PROPERTY_ENABLED = PREFIX + ".enabled";
    public static final String PROPERTY_ALLOWED_ENVIRONMENTS = PREFIX + ".allowed-environments";

    private boolean enabled = DEFAULT_ENABLED;

    private Set<String> allowedEnvironments = DEFAULT_ALLOWED_ENVIRONMENTS;

    /**
     * Enables/disables the control panel module. Default value: {@value #DEFAULT_ENABLED}.
     *
     * @param enabled {@code true} to enable the module, {@code false} to disable it.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Configures the environments where the control panel module is enabled. By default, it is
     * only enabled in the "dev" and "test" environments.
     *
     * @return the environments where the control panel module is enabled.
     */
    public Set<String> getAllowedEnvironments() {
        return allowedEnvironments;
    }

    /**
     * Configures the environments where the control panel module is enabled.
     *
     * @param allowedEnvironments the environments where the control panel module is enabled.
     */
    public void setAllowedEnvironments(Set<String> allowedEnvironments) {
        this.allowedEnvironments = allowedEnvironments;
    }
}
