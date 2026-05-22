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
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.ConfigurationInterceptUrlMapRule;
import io.micronaut.security.rules.SecurityRuleResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ControlPanelSecurityRuleTest {

    @Test
    void ruleRunsAfterHostInterceptUrlMapRules() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, null);

        assertEquals(ConfigurationInterceptUrlMapRule.ORDER + 50, rule.getOrder());
    }

    @Test
    void fallbackWriteAccessFilterOrderDoesNotDependOnSecurityClasses() throws IOException {
        assertEquals(ControlPanelWriteAccessFilter.ORDER, ServerFilterPhase.SECURITY.after());

        byte[] filterBytecode = readClassBytes(ControlPanelWriteAccessFilter.class);
        assertFalse(containsAscii(filterBytecode, "ControlPanelSecurityRule"));
        assertFalse(containsAscii(filterBytecode, "io/micronaut/security"));
    }

    @Test
    void anonymousModeAllowsTheControlPanelSurface() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.ANONYMOUS, null);

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.DELETE("/control-panel" + ControlPanelSecurityPaths.CACHE_PATH + "/demo"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.DATASOURCE_PATH + "/default/query", "{}"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/hql", "{}"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel" + ControlPanelSecurityPaths.OBJECT_STORAGE_PATH + "/local/object"), null));
    }

    @Test
    void authenticatedModeRejectsAnonymousControlPanelRequests() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, null);

        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel/routes"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.DELETE("/control-panel" + ControlPanelSecurityPaths.CACHE_PATH + "/demo"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/hql", "{}"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), null));
    }

    @Test
    void authenticatedModeAllowsAuthenticatedControlPanelRequests() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHENTICATED, "/app");
        Authentication authentication = Authentication.build("sherlock", Set.of("USER"), Map.of());

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/app/control-panel"), authentication));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/app/control-panel" + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/hql", "{}"), authentication));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/app/control-panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), authentication));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/app/control-panel" + ControlPanelSecurityPaths.OBJECT_STORAGE_PATH + "/local/object"), authentication));
    }

    @Test
    void authorizedModeRejectsAnonymousControlPanelRequests() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHORIZED, null);

        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel/routes"), null));
    }

    @Test
    void authorizedModeRejectsAuthenticatedUsersWithoutTheConfiguredRole() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHORIZED, null);
        Authentication authentication = Authentication.build("sherlock", Set.of("ROLE_USER"), Map.of());

        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/control-panel"), authentication));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.DELETE("/control-panel" + ControlPanelSecurityPaths.CACHE_PATH + "/demo"), authentication));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), authentication));
    }

    @Test
    void authorizedModeAllowsAuthenticatedUsersWithTheDefaultRole() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHORIZED, null);
        Authentication authentication = Authentication.build("sherlock", Set.of(ControlPanelSecurityConfiguration.DEFAULT_ROLE), Map.of());

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), authentication));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), authentication));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel" + ControlPanelSecurityPaths.OBJECT_STORAGE_PATH + "/local/object"), authentication));
    }

    @Test
    void authorizedModeAllowsAuthenticatedUsersWithTheCustomRole() {
        ControlPanelSecurityRule rule = newRule(ControlPanelSecurityConfiguration.Access.AUTHORIZED, null, ControlPanelModuleConfiguration.DEFAULT_PATH, "ROLE_ADMIN");
        Authentication authentication = Authentication.build("sherlock", Set.of("ROLE_ADMIN"), Map.of());

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), authentication));
    }

    @Test
    void deniedWriteAccessRejectsWriteRequestsWhileAllowingReads() {
        ControlPanelSecurityRule rule = newRule(
            ControlPanelSecurityConfiguration.Access.ANONYMOUS,
            null,
            ControlPanelModuleConfiguration.DEFAULT_PATH,
            ControlPanelSecurityConfiguration.DEFAULT_ROLE,
            ControlPanelSecurityConfiguration.WriteAccess.DENIED,
            null
        );

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/hql", "{}"), null));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel" + ControlPanelSecurityPaths.KAFKA_PATH + "/topics"), null));
        for (HttpRequest<?> request : writeRequests("/control-panel")) {
            assertEquals(SecurityRuleResult.REJECTED, check(rule, request, null));
        }
    }

    @Test
    void separateWriteRoleAllowsReadRoleToReadOnly() {
        ControlPanelSecurityRule rule = newRule(
            ControlPanelSecurityConfiguration.Access.AUTHORIZED,
            null,
            ControlPanelModuleConfiguration.DEFAULT_PATH,
            ControlPanelSecurityConfiguration.DEFAULT_ROLE,
            ControlPanelSecurityConfiguration.WriteAccess.AUTHORIZED,
            "ROLE_CONTROL_PANEL_WRITE"
        );
        Authentication reader = Authentication.build("reader", Set.of(ControlPanelSecurityConfiguration.DEFAULT_ROLE), Map.of());
        Authentication writer = Authentication.build("writer", Set.of("ROLE_CONTROL_PANEL_WRITE"), Map.of());

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), reader));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.DELETE("/control-panel" + ControlPanelSecurityPaths.CACHE_PATH + "/demo"), reader));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.KAFKA_PATH + "/messages", "{}"), reader));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.DELETE("/control-panel" + ControlPanelSecurityPaths.CACHE_PATH + "/demo"), writer));
        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.KAFKA_PATH + "/messages", "{}"), writer));
    }

    @Test
    void authenticatedWriteAccessFailsClosedWithoutAuthentication() {
        ControlPanelSecurityRule rule = newRule(
            ControlPanelSecurityConfiguration.Access.ANONYMOUS,
            null,
            ControlPanelModuleConfiguration.DEFAULT_PATH,
            ControlPanelSecurityConfiguration.DEFAULT_ROLE,
            ControlPanelSecurityConfiguration.WriteAccess.AUTHENTICATED,
            null
        );

        assertEquals(SecurityRuleResult.ALLOWED, check(rule, HttpRequest.GET("/control-panel"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/control-panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), null));
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
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.GET("/app/admin/panel" + ControlPanelSecurityPaths.CACHE_PATH + "/demo"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/app/admin/panel" + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/hql", "{}"), null));
        assertEquals(SecurityRuleResult.REJECTED, check(rule, HttpRequest.POST("/app/admin/panel" + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"), null));
        assertEquals(SecurityRuleResult.UNKNOWN, check(rule, HttpRequest.GET("/app/control-panel"), null));
    }

    private static ControlPanelSecurityRule newRule(ControlPanelSecurityConfiguration.Access access, String contextPath) {
        return newRule(access, contextPath, ControlPanelModuleConfiguration.DEFAULT_PATH);
    }

    private static ControlPanelSecurityRule newRule(ControlPanelSecurityConfiguration.Access access,
                                                    String contextPath,
                                                    String controlPanelPath) {
        return newRule(access, contextPath, controlPanelPath, ControlPanelSecurityConfiguration.DEFAULT_ROLE);
    }

    private static ControlPanelSecurityRule newRule(ControlPanelSecurityConfiguration.Access access,
                                                    String contextPath,
                                                    String controlPanelPath,
                                                    String role) {
        return newRule(access, contextPath, controlPanelPath, role, ControlPanelSecurityConfiguration.WriteAccess.INHERITED, null);
    }

    private static ControlPanelSecurityRule newRule(ControlPanelSecurityConfiguration.Access access,
                                                    String contextPath,
                                                    String controlPanelPath,
                                                    String role,
                                                    ControlPanelSecurityConfiguration.WriteAccess writeAccess,
                                                    String writeRole) {
        ControlPanelSecurityConfiguration securityConfiguration = new ControlPanelSecurityConfiguration(access, role, writeAccess, writeRole);
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

    private static List<HttpRequest<?>> writeRequests(String controlPanelPath) {
        return List.of(
            HttpRequest.DELETE(controlPanelPath + ControlPanelSecurityPaths.CACHE_PATH + "/demo"),
            HttpRequest.DELETE(controlPanelPath + ControlPanelSecurityPaths.CACHE_PATH + "/demo/key"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.DATASOURCE_PATH + "/default/query", "{}"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/statistics/enabled/true", ""),
            HttpRequest.DELETE(controlPanelPath + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/statistics"),
            HttpRequest.DELETE(controlPanelPath + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/cache"),
            HttpRequest.DELETE(controlPanelPath + ControlPanelSecurityPaths.HIBERNATE_PATH + "/default/cache/region?region=books"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.LOGGERS_PATH + "/ROOT", "{}"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.KAFKA_PATH + "/messages", "{}"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.KAFKA_PATH + "/topics", "{}"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.APPLICATION_PATH + "/refresh", "{}"),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.APPLICATION_PATH + "/stop", ""),
            HttpRequest.POST(controlPanelPath + ControlPanelSecurityPaths.OBJECT_STORAGE_PATH + "/default", ""),
            HttpRequest.DELETE(controlPanelPath + ControlPanelSecurityPaths.OBJECT_STORAGE_PATH + "/default/hello.txt")
        );
    }

    private static SecurityRuleResult check(ControlPanelSecurityRule rule,
                                            HttpRequest<?> request,
                                            Authentication authentication) {
        return Mono.from(rule.check(request, authentication)).block();
    }

    private static byte[] readClassBytes(Class<?> type) throws IOException {
        String resourceName = type.getSimpleName() + ".class";
        try (InputStream inputStream = Objects.requireNonNull(type.getResourceAsStream(resourceName))) {
            return inputStream.readAllBytes();
        }
    }

    private static boolean containsAscii(byte[] bytes, String value) {
        byte[] needle = value.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            boolean matches = true;
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
