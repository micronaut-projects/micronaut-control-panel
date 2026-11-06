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
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Configuration for the control panel request-origin protections applied to mutating HTTP requests
 * and for the host allowlist used when the control panel is reachable anonymously.
 *
 * @since 2.2.1
 */
@ConfigurationProperties(ControlPanelRequestConfiguration.PREFIX)
public interface ControlPanelRequestConfiguration {

    String PREFIX = ControlPanelSecurityConfiguration.PREFIX;
    String DEFAULT_CSRF_PROTECTION_ENABLED = "true";
    String DEFAULT_HOST_CHECK_ENABLED = "true";

    /**
     * Whether same-origin protection (Fetch Metadata, {@code Origin} and the control panel request
     * header) is enforced for mutating control panel requests. Default: {@value #DEFAULT_CSRF_PROTECTION_ENABLED}.
     *
     * @return whether cross-site request protection is enabled
     */
    @Bindable(defaultValue = DEFAULT_CSRF_PROTECTION_ENABLED)
    boolean isCsrfProtectionEnabled();

    /**
     * Whether the host allowlist is enforced when the control panel is reachable without
     * authentication. Default: {@value #DEFAULT_HOST_CHECK_ENABLED}.
     *
     * @return whether the host check is enabled
     */
    @Bindable(defaultValue = DEFAULT_HOST_CHECK_ENABLED)
    boolean isHostCheckEnabled();

    /**
     * Additional host names (optionally with a port) that are accepted when the host check is
     * active, beyond the built-in loopback names. Each entry is matched case-insensitively and may
     * be a bare host or {@code host:port}.
     *
     * @return the configured additional allowed hosts
     */
    @Bindable(defaultValue = "")
    @Nullable
    Set<String> getAllowedHosts();
}
