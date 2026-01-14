package io.micronaut.controlpanel.ui;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlPanelControllerPathTest {

    @Test
    void controlPanelIsAccessibleInTheDefaultPath() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        var ctx = server.getApplicationContext();
        var client = ctx.createBean(HttpClient.class, server.getURL()).toBlocking();
        var status = client.exchange(ControlPanelModuleConfiguration.DEFAULT_PATH).status();
        assertEquals(HttpStatus.OK, status);
        server.stop();
    }

    @Test
    void controlPanelPathIsConfigurable() {
        String path = "/cp";
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, java.util.Map.of(ControlPanelModuleConfiguration.PROPERTY_PATH, path));
        var ctx = server.getApplicationContext();
        var client = ctx.createBean(HttpClient.class, server.getURL()).toBlocking();
        var status = client.exchange(path).status();
        assertEquals(HttpStatus.OK, status);
        server.stop();
    }

    @Test
    void controlPanelHtmlTemplatesUseTheCustomPathInLinks() {
        String customPath = "/admin";
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, java.util.Map.of(ControlPanelModuleConfiguration.PROPERTY_PATH, customPath));
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
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, java.util.Map.of(
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
}
