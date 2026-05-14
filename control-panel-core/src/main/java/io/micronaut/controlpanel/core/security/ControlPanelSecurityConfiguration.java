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
import org.jspecify.annotations.Nullable;

/**
 * Security configuration for the control panel HTTP surface.
 *
 * @param access the configured read access mode
 * @param role role required when authorized read access is enabled
 * @param writeAccess the configured write access mode
 * @param writeRole role required when authorized write access is enabled
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@ConfigurationProperties(ControlPanelSecurityConfiguration.PREFIX)
public record ControlPanelSecurityConfiguration(
    /**
     * Controls how the control panel HTTP read routes are exposed.
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
    String role,

    /**
     * Controls how the control panel HTTP write routes are exposed.
     *
     * @return the configured write access mode
     */
    @Bindable(defaultValue = DEFAULT_WRITE_ACCESS)
    WriteAccess writeAccess,

    /**
     * Role required when {@link WriteAccess#AUTHORIZED} mode is active. When unset, the read role is used.
     *
     * @return the configured write role
     */
    @Nullable
    String writeRole
) {

    public static final String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".security";
    public static final String PROPERTY_ACCESS = PREFIX + ".access";
    public static final String PROPERTY_ROLE = PREFIX + ".role";
    public static final String PROPERTY_WRITE_ACCESS = PREFIX + ".write-access";
    public static final String PROPERTY_WRITE_ROLE = PREFIX + ".write-role";
    public static final String DEFAULT_ACCESS = "AUTHORIZED";
    public static final String DEFAULT_ROLE = "ROLE_CONTROL_PANEL";
    public static final String DEFAULT_WRITE_ACCESS = "INHERITED";

    /**
     * Creates configuration with the default control panel role.
     *
     * @param access the configured access mode
     */
    public ControlPanelSecurityConfiguration(Access access) {
        this(access, DEFAULT_ROLE);
    }

    /**
     * Creates configuration with inherited write access.
     *
     * @param access the configured read access mode
     * @param role the configured read role
     */
    public ControlPanelSecurityConfiguration(Access access, String role) {
        this(access, role, WriteAccess.INHERITED, null);
    }

    public ControlPanelSecurityConfiguration {
        if (access == Access.AUTHORIZED && role.isBlank()) {
            throw new IllegalArgumentException(
                "Control panel security role cannot be blank when authorized access is enabled"
            );
        }
        if (writeRole != null && writeRole.isBlank()) {
            throw new IllegalArgumentException(
                "Control panel security write role cannot be blank when configured"
            );
        }
        String effectiveWriteRole = writeRole == null ? role : writeRole;
        if (writeAccess == WriteAccess.AUTHORIZED && effectiveWriteRole.isBlank()) {
            throw new IllegalArgumentException(
                "Control panel security write role cannot be blank when authorized write access is enabled"
            );
        }
    }

    /**
     * Resolve the effective write role.
     *
     * @return the write role, falling back to the read role when no write role is configured
     */
    public String effectiveWriteRole() {
        return writeRole == null ? role : writeRole;
    }

    /**
     * Available access modes for the control panel HTTP surface.
     */
    enum Access {
        ANONYMOUS,
        AUTHENTICATED,
        AUTHORIZED
    }

    /**
     * Available write access modes for mutating control panel HTTP operations.
     */
    enum WriteAccess {
        INHERITED,
        ANONYMOUS,
        AUTHENTICATED,
        AUTHORIZED,
        DENIED
    }
}
