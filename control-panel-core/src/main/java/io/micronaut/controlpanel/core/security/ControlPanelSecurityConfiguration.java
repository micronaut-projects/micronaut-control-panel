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
package io.micronaut.controlpanel.core.security;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Security configuration for the control panel HTTP surface.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@ConfigurationProperties(ControlPanelSecurityConfiguration.PREFIX)
public interface ControlPanelSecurityConfiguration {

    String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".security";
    String PROPERTY_ACCESS = PREFIX + ".access";
    String DEFAULT_ACCESS = "AUTHENTICATED";

    /**
     * Controls how the control panel HTTP routes are exposed.
     *
     * @return the configured access mode
     */
    @Bindable(defaultValue = DEFAULT_ACCESS)
    Access getAccess();

    /**
     * Available access modes for the control panel HTTP surface.
     */
    enum Access {
        ANONYMOUS,
        AUTHENTICATED
    }
}
