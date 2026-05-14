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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;
import io.micronaut.security.rules.ConfigurationInterceptUrlMapRule;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.micronaut.controlpanel.util.ControlPanelUtils.computeControlPanelPath;

/**
 * Applies the configured access mode to the control-panel-owned HTTP routes when Micronaut Security is active.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Requires(beans = SecurityFilter.class)
@Singleton
@Internal
public final class ControlPanelSecurityRule implements SecurityRule<HttpRequest<?>> {

    static final int ORDER = ConfigurationInterceptUrlMapRule.ORDER + 50;

    private final ControlPanelSecurityConfiguration.Access access;
    private final String role;
    private final ControlPanelSecurityConfiguration.WriteAccess writeAccess;
    private final String writeRole;
    private final String controlPanelPath;
    private final List<String> protectedRoutePrefixes;

    public ControlPanelSecurityRule(ControlPanelSecurityConfiguration securityConfiguration,
                                    ControlPanelModuleConfiguration moduleConfiguration,
                                    HttpServerConfiguration serverConfiguration) {
        this.access = securityConfiguration.access();
        this.role = securityConfiguration.role();
        this.writeAccess = securityConfiguration.writeAccess();
        this.writeRole = securityConfiguration.effectiveWriteRole();
        String applicationPath = Optional.ofNullable(serverConfiguration.getContextPath()).orElse("");
        this.controlPanelPath = computeControlPanelPath(applicationPath, moduleConfiguration.getPath());
        List<String> prefixes = new ArrayList<>();
        prefixes.add(controlPanelPath);
        for (String helperPath : ControlPanelSecurityPaths.helperPaths()) {
            prefixes.add(controlPanelPath + helperPath);
        }
        this.protectedRoutePrefixes = List.copyOf(prefixes);
    }

    @Override
    public Publisher<SecurityRuleResult> check(@Nullable HttpRequest<?> request,
                                               @Nullable Authentication authentication) {
        if (request == null || !matches(request.getPath())) {
            return Publishers.just(SecurityRuleResult.UNKNOWN);
        }
        if (ControlPanelSecurityPaths.isWriteRequest(controlPanelPath, request)) {
            return Publishers.just(writeResult(authentication));
        }
        return Publishers.just(readResult(authentication));
    }

    private SecurityRuleResult writeResult(@Nullable Authentication authentication) {
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.INHERITED) {
            return readResult(authentication);
        }
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.ANONYMOUS) {
            return SecurityRuleResult.ALLOWED;
        }
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.AUTHENTICATED && authentication != null) {
            return SecurityRuleResult.ALLOWED;
        }
        if (writeAccess == ControlPanelSecurityConfiguration.WriteAccess.AUTHORIZED
            && authentication != null
            && authentication.getRoles().contains(writeRole)) {
            return SecurityRuleResult.ALLOWED;
        }
        return SecurityRuleResult.REJECTED;
    }

    private SecurityRuleResult readResult(@Nullable Authentication authentication) {
        if (access == ControlPanelSecurityConfiguration.Access.ANONYMOUS) {
            return SecurityRuleResult.ALLOWED;
        }
        if (access == ControlPanelSecurityConfiguration.Access.AUTHENTICATED && authentication != null) {
            return SecurityRuleResult.ALLOWED;
        }
        if (access == ControlPanelSecurityConfiguration.Access.AUTHORIZED
            && authentication != null
            && authentication.getRoles().contains(role)) {
            return SecurityRuleResult.ALLOWED;
        }
        return SecurityRuleResult.REJECTED;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    boolean matches(String path) {
        return protectedRoutePrefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
