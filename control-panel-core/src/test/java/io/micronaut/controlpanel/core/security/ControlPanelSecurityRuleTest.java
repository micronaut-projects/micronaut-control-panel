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

import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.ConfigurationInterceptUrlMapRule;
import io.micronaut.security.rules.SecurityRuleResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlPanelSecurityRuleTest {

    @Test
    void ruleRunsAfterHostInterceptUrlMapRules() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, null);

        assertEquals(ConfigurationInterceptUrlMapRule.ORDER + 50, rule.getOrder());
    }

    @Test
    void anonymousModeAllowsTheControlPanelSurface() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.ANONYMOUS, null);

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.DELETE(ControlPanelSecurityPaths.CACHE + "/demo"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST(ControlPanelSecurityPaths.DATASOURCE + "/default/query", "{}"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET(ControlPanelSecurityPaths.OBJECT_STORAGE + "/local/object"), null));
    }

    @Test
    void authenticatedModeRejectsAnonymousControlPanelRequests() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, null);

        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel/routes"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.DELETE(ControlPanelSecurityPaths.CACHE + "/demo"), null));
    }

    @Test
    void authenticatedModeAllowsAuthenticatedControlPanelRequests() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, "/app");
        Authentication authentication = Authentication.build("sherlock", Set.of("USER"), Map.of());

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/app/control-panel"), authentication));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/app" + ControlPanelSecurityPaths.OBJECT_STORAGE + "/local/object"), authentication));
    }

    @Test
    void nonControlPanelRoutesRemainUnknown() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, null);

        assertEquals(SecurityRuleResult.UNKNOWN, check(rule, HttpRequest.GET("/health"), null));
        assertEquals(SecurityRuleResult.UNKNOWN, check(rule, HttpRequest.GET("/demo"), Authentication.build("watson")));
        assertEquals(SecurityRuleResult.UNKNOWN, check(rule, HttpRequest.GET("/micronaut-control-panel/js/editor.bundle.js"), null));
    }

    @Test
    void configuredControlPanelPathIsProtectedWithContextPath() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, "/app", "/admin/panel");

        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/app/admin/panel"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/app/admin/panel/routes"), null));
        assertEquals(SecurityRuleResult.UNKNOWN, check(rule, HttpRequest.GET("/app/control-panel"), null));
    }

    private static ControlPanelSecurityRule newRule(ControlPanelSecurityConfiguration.Access access, String contextPath) {
        return newRule(access, contextPath, ControlPanelModuleConfiguration.DEFAULT_PATH);
    }

    private static ControlPanelSecurityRule newRule(ControlPanelSecurityConfiguration.Access access,
                                                    String contextPath,
                                                    String controlPanelPath) {
        ControlPanelSecurityConfiguration securityConfiguration = () -> access;
        ControlPanelModuleConfiguration moduleConfiguration = new ControlPanelModuleConfiguration() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public Set<String> getAllowedEnvironments() {
                return Set.of("test");
            }

            @Override
            public String getPath() {
                return controlPanelPath;
            }

            @Override
            public boolean isLogUrl() {
                return true;
            }
        };
        HttpServerConfiguration serverConfiguration = new HttpServerConfiguration();
        if (contextPath != null) {
            serverConfiguration.setContextPath(contextPath);
        }
        return new ControlPanelSecurityRule(securityConfiguration, moduleConfiguration, serverConfiguration);
    }

    private static SecurityRuleResult check(ControlPanelSecurityRule rule,
                                            HttpRequest<?> request,
                                            Authentication authentication) {
        return Mono.from(rule.check(request, authentication)).block();
    }
}
