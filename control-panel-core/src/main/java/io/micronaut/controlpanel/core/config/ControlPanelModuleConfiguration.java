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
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;

import java.util.Set;

/**
 * Configuration properties for the control panel module.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@ConfigurationProperties(ControlPanelModuleConfiguration.PREFIX)
public interface ControlPanelModuleConfiguration extends Toggleable {

    String DEFAULT_ENABLED = "true";
    String DEFAULT_ALLOWED_ENVIRONMENTS = Environment.DEVELOPMENT + "," + Environment.TEST;

    String DEFAULT_PATH = "/control-panel";

    String PREFIX = "micronaut.control-panel";
    String PROPERTY_ENABLED = PREFIX + ".enabled";
    String PROPERTY_ALLOWED_ENVIRONMENTS = PREFIX + ".allowed-environments";
    String PROPERTY_PATH = PREFIX + ".path";

    /**
     * Enables/disables the control panel module. Default value: {@value #DEFAULT_ENABLED}.
     */
    @Override
    @Bindable(defaultValue = DEFAULT_ENABLED)
    boolean isEnabled();

    /**
     * Configures the environments where the control panel module is enabled. By default, it is
     * only enabled in the "dev" and "test" environments.
     *
     * @return the environments where the control panel module is enabled.
     */
    @Bindable(defaultValue = DEFAULT_ALLOWED_ENVIRONMENTS)
    Set<String> getAllowedEnvironments();

    /**
     * Configures the path where the control panel can be accessed. Default value: {@value #DEFAULT_PATH}.
     *
     * @return the path where the control panel module is available.
     */
    @Bindable(defaultValue = DEFAULT_PATH)
    String getPath();

}
