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
package io.micronaut.controlpanel.ui;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityConfiguration;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlPanelSecurityTest {

    @Test
    void authorizedAccessIsRequiredByDefaultWhenSecurityIsEnabled() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ControlPanelSecurityTest",
            "micronaut.security.enabled", true,
            "micronaut.security.basic-auth.enabled", true
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

            HttpClientResponseException anonymous = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(ControlPanelModuleConfiguration.DEFAULT_PATH)
            );
            assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatus());

            HttpClientResponseException missingRole = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH))
                )
            );
            assertEquals(HttpStatus.FORBIDDEN, missingRole.getStatus());

            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "controlpanel", "password")
            ).status());

            client.close();
        }
    }

    @Test
    void authenticatedAccessCanBeRequiredWhenSecurityIsEnabled() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ControlPanelSecurityTest",
            "micronaut.security.enabled", true,
            "micronaut.security.basic-auth.enabled", true,
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "AUTHENTICATED"
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

            HttpClientResponseException anonymous = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(ControlPanelModuleConfiguration.DEFAULT_PATH)
            );
            assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatus());

            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH))
            ).status());

            client.close();
        }
    }

    @Test
    void anonymousAccessCanBeConfiguredExplicitlyWhenSecurityIsEnabled() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ControlPanelSecurityTest",
            "micronaut.security.enabled", true,
            "micronaut.security.basic-auth.enabled", true,
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS"
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(ControlPanelModuleConfiguration.DEFAULT_PATH).status());

            client.close();
        }
    }

    @Test
    void authorizedAccessUsesTheConfiguredRoleWhenSecurityIsEnabled() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ControlPanelSecurityTest",
            "micronaut.security.enabled", true,
            "micronaut.security.basic-auth.enabled", true,
            ControlPanelSecurityConfiguration.PROPERTY_ROLE, "ROLE_ADMIN"
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

            HttpClientResponseException missingRole = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "controlpanel", "password")
                )
            );
            assertEquals(HttpStatus.FORBIDDEN, missingRole.getStatus());

            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "admin", "password")
            ).status());

            client.close();
        }
    }

    @Test
    void authenticatedAccessProtectsHelperControllersAtHttpLevel(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "hello");

        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.ofEntries(
            Map.entry("spec.name", "ControlPanelSecurityTest"),
            Map.entry("micronaut.security.enabled", true),
            Map.entry("micronaut.security.basic-auth.enabled", true),
            Map.entry(ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "AUTHENTICATED"),
            Map.entry("micronaut.caches.demo.initial-capacity", 1),
            Map.entry("datasources.default.db-type", "h2"),
            Map.entry("datasources.default.dialect", "H2"),
            Map.entry("datasources.default.url", "jdbc:h2:mem:control-panel-security;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"),
            Map.entry("datasources.default.driver-class-name", "org.h2.Driver"),
            Map.entry("datasources.default.username", "sa"),
            Map.entry("datasources.default.password", ""),
            Map.entry("micronaut.object-storage.local.default.path", tempDir.toString())
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            for (String helperPath : routedHelperPaths()) {
                HttpClientResponseException anonymous = assertThrows(
                    HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET(helperPath))
                );
                assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatus());
            }

            assertEquals(HttpStatus.NO_CONTENT, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.DELETE(helperPath(ControlPanelSecurityPaths.CACHE_PATH, "/demo")))
            ).status());
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(helperPath(ControlPanelSecurityPaths.DATASOURCE_PATH, "/default/schema.js")))
            ).status());
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(helperPath(ControlPanelSecurityPaths.OBJECT_STORAGE_PATH, "/default/hello.txt")))
            ).status());

            client.close();
        }
    }

    @Test
    void helperControllersHonorConfiguredControlPanelPath() {
        String controlPanelPath = "/admin/panel";
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.ofEntries(
            Map.entry("spec.name", "ControlPanelSecurityTest"),
            Map.entry("micronaut.security.enabled", true),
            Map.entry("micronaut.security.basic-auth.enabled", true),
            Map.entry(ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "AUTHENTICATED"),
            Map.entry(ControlPanelModuleConfiguration.PROPERTY_PATH, controlPanelPath),
            Map.entry("micronaut.caches.demo.initial-capacity", 1)
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

            assertEquals(HttpStatus.NO_CONTENT, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.DELETE(helperPath(controlPanelPath, ControlPanelSecurityPaths.CACHE_PATH, "/demo")))
            ).status());

            client.close();
        }
    }

    @Test
    void hostInterceptUrlMapRulesAreAuthoritativeInAnonymousMode() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.ofEntries(
            Map.entry("spec.name", "ControlPanelSecurityTest"),
            Map.entry("micronaut.security.enabled", true),
            Map.entry("micronaut.security.basic-auth.enabled", true),
            Map.entry(ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS"),
            Map.entry("micronaut.security.intercept-url-map[0].pattern", ControlPanelModuleConfiguration.DEFAULT_PATH + "/**"),
            Map.entry("micronaut.security.intercept-url-map[0].access[0]", "ROLE_ADMIN")
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

            HttpClientResponseException nonAdmin = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "user", "password"))
            );
            assertEquals(HttpStatus.FORBIDDEN, nonAdmin.getStatus());

            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "admin", "password")
            ).status());

            client.close();
        }
    }

    @Test
    void hostInterceptUrlMapRulesAreAuthoritativeInAuthenticatedMode() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.ofEntries(
            Map.entry("spec.name", "ControlPanelSecurityTest"),
            Map.entry("micronaut.security.enabled", true),
            Map.entry("micronaut.security.basic-auth.enabled", true),
            Map.entry(ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "AUTHENTICATED"),
            Map.entry("micronaut.security.intercept-url-map[0].pattern", ControlPanelModuleConfiguration.DEFAULT_PATH + "/**"),
            Map.entry("micronaut.security.intercept-url-map[0].access[0]", "ROLE_ADMIN"),
            Map.entry("micronaut.security.intercept-url-map[1].pattern", helperPath(ControlPanelSecurityPaths.CACHE_PATH, "/**")),
            Map.entry("micronaut.security.intercept-url-map[1].access[0]", "ROLE_ADMIN")
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());

            HttpClientResponseException nonAdminControlPanel = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "user", "password"))
            );
            assertEquals(HttpStatus.FORBIDDEN, nonAdminControlPanel.getStatus());

            HttpClientResponseException nonAdminHelper = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(authenticatedRequest(HttpRequest.DELETE(helperPath(ControlPanelSecurityPaths.CACHE_PATH, "/demo")), "user", "password"))
            );
            assertEquals(HttpStatus.FORBIDDEN, nonAdminHelper.getStatus());

            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH), "admin", "password")
            ).status());

            client.close();
        }
    }

    private static List<String> routedHelperPaths() {
        return List.of(
            helperPath(ControlPanelSecurityPaths.CACHE_PATH, "/demo"),
            helperPath(ControlPanelSecurityPaths.DATASOURCE_PATH, "/default/schema.js"),
            helperPath(ControlPanelSecurityPaths.OBJECT_STORAGE_PATH, "/default/hello.txt")
        );
    }

    private static String helperPath(String helperPath, String route) {
        return helperPath(ControlPanelModuleConfiguration.DEFAULT_PATH, helperPath, route);
    }

    private static String helperPath(String controlPanelPath, String helperPath, String route) {
        return controlPanelPath + helperPath + route;
    }

    private static MutableHttpRequest<?> authenticatedRequest(MutableHttpRequest<?> request) {
        return authenticatedRequest(request, "user", "password");
    }

    private static MutableHttpRequest<?> authenticatedRequest(MutableHttpRequest<?> request,
                                                             String username,
                                                             String password) {
        return request.header("Authorization", basicAuthorization(username, password));
    }

    private static String basicAuthorization(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Requires(property = "spec.name", value = "ControlPanelSecurityTest")
    @Singleton
    static class TestAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

        @Override
        public AuthenticationResponse authenticate(io.micronaut.http.HttpRequest<B> requestContext,
                                                   AuthenticationRequest<String, String> authRequest) {
            if ("password".equals(authRequest.getSecret())) {
                if ("admin".equals(authRequest.getIdentity())) {
                    return AuthenticationResponse.success("admin", List.of("ROLE_ADMIN"));
                }
                if ("controlpanel".equals(authRequest.getIdentity())) {
                    return AuthenticationResponse.success("controlpanel", List.of(ControlPanelSecurityConfiguration.DEFAULT_ROLE));
                }
                if ("user".equals(authRequest.getIdentity())) {
                    return AuthenticationResponse.success("user", List.of("ROLE_USER"));
                }
            }
            return AuthenticationResponse.failure();
        }
    }
}
