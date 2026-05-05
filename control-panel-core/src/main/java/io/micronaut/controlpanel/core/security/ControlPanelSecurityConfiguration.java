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
 * @param access the configured access mode
 * @param role role required when authorized access is enabled
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@ConfigurationProperties(ControlPanelSecurityConfiguration.PREFIX)
public record ControlPanelSecurityConfiguration(
    /**
     * Controls how the control panel HTTP routes are exposed.
     *
     * @return the configured access mode
     */
    @Bindable(defaultValue = DEFAULT_ACCESS)
    Access access,

    /**
     * Role required when {@link Access#AUTHORIZED} mode is active.
     *
     * @return the configured role
     */
    @Bindable(defaultValue = DEFAULT_ROLE)
    String role
) {

    public static final String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".security";
    public static final String PROPERTY_ACCESS = PREFIX + ".access";
    public static final String PROPERTY_ROLE = PREFIX + ".role";
    public static final String DEFAULT_ACCESS = "AUTHORIZED";
    public static final String DEFAULT_ROLE = "ROLE_CONTROL_PANEL";

    /**
     * Creates configuration with the default control panel role.
     *
     * @param access the configured access mode
     */
    public ControlPanelSecurityConfiguration(Access access) {
        this(access, DEFAULT_ROLE);
    }

    public ControlPanelSecurityConfiguration {
        if (access == Access.AUTHORIZED && role.isBlank()) {
            throw new IllegalArgumentException(
                "Control panel security role cannot be blank when authorized access is enabled"
            );
        }
    }

    /**
     * Available access modes for the control panel HTTP surface.
     */
    enum Access {
        ANONYMOUS,
        AUTHENTICATED,
        AUTHORIZED
    }
}
