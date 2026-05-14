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

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates write access from Micronaut Security authentication.
 */
@Requires(beans = SecurityFilter.class)
@Primary
@Singleton
@Internal
final class SecurityControlPanelWriteAccessEvaluator implements ControlPanelWriteAccessEvaluator {

    private final ControlPanelSecurityConfiguration securityConfiguration;

    SecurityControlPanelWriteAccessEvaluator(ControlPanelSecurityConfiguration securityConfiguration) {
        this.securityConfiguration = securityConfiguration;
    }

    @Override
    public ControlPanelWriteAccess evaluate(HttpRequest<?> request) {
        return request.getUserPrincipal(Authentication.class)
            .map(this::evaluateAuthentication)
            .orElseGet(() -> evaluateAuthentication(null));
    }

    private ControlPanelWriteAccess evaluateAuthentication(@Nullable Authentication authentication) {
        if (isAllowed(authentication)) {
            return ControlPanelWriteAccess.allowedAccess();
        }
        return ControlPanelWriteAccess.deniedAccess();
    }

    private boolean isAllowed(@Nullable Authentication authentication) {
        ControlPanelSecurityConfiguration.WriteAccess writeAccess = securityConfiguration.writeAccess();
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.INHERITED) {
            return isReadAllowed(authentication);
        }
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.ANONYMOUS) {
            return true;
        }
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.AUTHENTICATED) {
            return authentication != null;
        }
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.AUTHORIZED) {
            return authentication != null && authentication.getRoles().contains(securityConfiguration.effectiveWriteRole());
        }
        return false;
    }

    private boolean isReadAllowed(@Nullable Authentication authentication) {
        if (securityConfiguration.access() == ControlPanelSecurityConfiguration.Access.ANONYMOUS) {
            return true;
        }
        if (securityConfiguration.access() == ControlPanelSecurityConfiguration.Access.AUTHENTICATED) {
            return authentication != null;
        }
        return authentication != null && authentication.getRoles().contains(securityConfiguration.role());
    }
}
