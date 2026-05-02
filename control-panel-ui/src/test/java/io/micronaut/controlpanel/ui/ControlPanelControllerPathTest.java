/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ControlPanelControllerPathTest {

    @Test
    void controlPanelIsAccessibleInTheDefaultPath() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, anonymousControlPanelAccess());
        var ctx = server.getApplicationContext();
        var client = ctx.createBean(HttpClient.class, server.getURL()).toBlocking();
        var status = client.exchange(ControlPanelModuleConfiguration.DEFAULT_PATH).status();
        assertEquals(HttpStatus.OK, status);
        server.stop();
    }

    @Test
    void controlPanelPathIsConfigurable() {
        String path = "/cp";
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, anonymousControlPanelAccess(ControlPanelModuleConfiguration.PROPERTY_PATH, path));
        var ctx = server.getApplicationContext();
        var client = ctx.createBean(HttpClient.class, server.getURL()).toBlocking();
        var status = client.exchange(path).status();
        assertEquals(HttpStatus.OK, status);
        server.stop();
    }

    @Test
    void controlPanelHtmlTemplatesUseTheCustomPathInLinks() {
        String customPath = "/admin";
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, anonymousControlPanelAccess(ControlPanelModuleConfiguration.PROPERTY_PATH, customPath));
        var ctx = server.getApplicationContext();
        var client = ctx.createBean(HttpClient.class, server.getURL()).toBlocking();
        var response = client.exchange(customPath, String.class);
        assertEquals(HttpStatus.OK, response.status());
        String html = response.body();
        assertTrue(html.contains("href=\"/admin\""));
        assertTrue(html.contains("href=\"/admin/categories/"));
        assertTrue(html.contains("\"/admin/"));
        assertFalse(html.contains("\"/control-panel/"));
        server.stop();
    }

    @Test
    void controlPanelHonoursApplicationContextPath() {
        String appPath = "/app";
        String controlPanelPath = "/cp";
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, anonymousControlPanelAccess(
            "micronaut.server.context-path", appPath,
            ControlPanelModuleConfiguration.PROPERTY_PATH, controlPanelPath
        ));
        var ctx = server.getApplicationContext();
        var client = ctx.createBean(HttpClient.class, server.getURL()).toBlocking();
        var response = client.exchange("/app/cp", String.class);
        assertEquals(HttpStatus.OK, response.status());
        String html = response.body();
        assertTrue(html.contains("href=\"/app/cp\""));
        assertTrue(html.contains("href=\"/app/cp/categories/"));
        assertTrue(html.contains("\"/app/cp/"));
        assertFalse(html.contains("\"/control-panel/"));
        server.stop();
    }

    private static Map<String, Object> anonymousControlPanelAccess() {
        return Map.of(ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS");
    }

    private static Map<String, Object> anonymousControlPanelAccess(String firstProperty,
                                                                   Object firstValue,
                                                                   String secondProperty,
                                                                   Object secondValue) {
        return Map.of(
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS",
            firstProperty, firstValue,
            secondProperty, secondValue
        );
    }

    private static Map<String, Object> anonymousControlPanelAccess(String property, Object value) {
        return Map.of(
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS",
            property, value
        );
    }
}
