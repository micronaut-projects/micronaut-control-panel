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
    void anonymousAccessRemainsAvailableByDefaultWhenSecurityIsEnabled() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ControlPanelSecurityTest",
            "micronaut.security.enabled", true,
            "micronaut.security.basic-auth.enabled", true
        ))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(ControlPanelModuleConfiguration.DEFAULT_PATH).status());
            client.close();
        }
    }

    @Test
    void authenticatedAccessRejectsAnonymousRequestsAndAllowsAuthenticatedOnes() {
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

            HttpRequest<?> authenticatedRequest = HttpRequest.GET(ControlPanelModuleConfiguration.DEFAULT_PATH)
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8)));
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(authenticatedRequest).status());

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
            for (String helperPath : helperPaths()) {
                HttpClientResponseException anonymous = assertThrows(
                    HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET(helperPath))
                );
                assertEquals(HttpStatus.UNAUTHORIZED, anonymous.getStatus());
            }

            assertEquals(HttpStatus.NO_CONTENT, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.DELETE(ControlPanelSecurityPaths.CACHE + "/demo"))
            ).status());
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelSecurityPaths.DATASOURCE + "/default/schema.js"))
            ).status());
            assertEquals(HttpStatus.OK, client.toBlocking().exchange(
                authenticatedRequest(HttpRequest.GET(ControlPanelSecurityPaths.OBJECT_STORAGE + "/default/hello.txt"))
            ).status());

            client.close();
        }
    }

    private static List<String> helperPaths() {
        return List.of(
            ControlPanelSecurityPaths.CACHE + "/demo",
            ControlPanelSecurityPaths.DATASOURCE + "/default/schema.js",
            ControlPanelSecurityPaths.OBJECT_STORAGE + "/default/hello.txt"
        );
    }

    private static MutableHttpRequest<?> authenticatedRequest(MutableHttpRequest<?> request) {
        return request.header("Authorization", basicAuthorization());
    }

    private static String basicAuthorization() {
        return "Basic " + Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8));
    }

    @Requires(property = "spec.name", value = "ControlPanelSecurityTest")
    @Singleton
    static class TestAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

        @Override
        public AuthenticationResponse authenticate(io.micronaut.http.HttpRequest<B> requestContext,
                                                   AuthenticationRequest<String, String> authRequest) {
            if ("user".equals(authRequest.getIdentity()) && "password".equals(authRequest.getSecret())) {
                return AuthenticationResponse.success("user");
            }
            return AuthenticationResponse.failure();
        }
    }
}
