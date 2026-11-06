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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Checks that the cross-site request filter and the host filter apply to the control panel paths
 * only, and leave the application's other routes untouched.
 */
class ControlPanelRequestFiltersTest {

    private static final String SPEC_NAME = "ControlPanelRequestFiltersTest";
    private static final String FOREIGN_HOST = "attacker.example";

    @Test
    void filtersOnlyApplyToTheControlPanelPaths() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties(Map.of()))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            BlockingHttpClient blocking = client.toBlocking();
            String controlPanel = ControlPanelModuleConfiguration.DEFAULT_PATH;

            assertRejected(blocking, crossSite(HttpRequest.POST(controlPanel + "/filter-test", "")));
            assertRejected(blocking, crossSite(HttpRequest.DELETE(controlPanel + ControlPanelSecurityPaths.CACHE_PATH + "/demo")));
            assertRejected(blocking, foreignHost(HttpRequest.GET(controlPanel)));
            assertRejected(blocking, foreignHost(HttpRequest.GET(controlPanel + "/filter-test")));
            assertEquals(HttpStatus.OK, blocking.exchange(controlPanelRequest(HttpRequest.POST(controlPanel + "/filter-test", ""))).status());

            assertEquals(HttpStatus.OK, blocking.exchange(crossSite(HttpRequest.POST("/filter-test", ""))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(crossSite(HttpRequest.POST(controlPanel + "-other", ""))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(foreignHost(HttpRequest.GET("/filter-test"))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(foreignHost(HttpRequest.GET(controlPanel + "-other"))).status());

            client.close();
        }
    }

    @Test
    void filtersFollowTheContextPathAndConfiguredControlPanelPath() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties(Map.of(
            "micronaut.server.context-path", "/app",
            ControlPanelModuleConfiguration.PROPERTY_PATH, "/cp"
        )))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            BlockingHttpClient blocking = client.toBlocking();

            assertRejected(blocking, crossSite(HttpRequest.POST("/app/cp/filter-test", "")));
            assertRejected(blocking, foreignHost(HttpRequest.GET("/app/cp")));
            assertEquals(HttpStatus.OK, blocking.exchange(controlPanelRequest(HttpRequest.POST("/app/cp/filter-test", ""))).status());

            assertEquals(HttpStatus.OK, blocking.exchange(crossSite(HttpRequest.POST("/app/filter-test", ""))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(foreignHost(HttpRequest.GET("/app/filter-test"))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(crossSite(HttpRequest.POST("/app/control-panel/filter-test", ""))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(foreignHost(HttpRequest.GET("/app/control-panel/filter-test"))).status());

            client.close();
        }
    }

    @Test
    void filtersApplyToAControlPanelPathConfiguredWithoutALeadingSlash() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties(Map.of(
            ControlPanelModuleConfiguration.PROPERTY_PATH, "cp"
        )))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            BlockingHttpClient blocking = client.toBlocking();

            assertRejected(blocking, crossSite(HttpRequest.POST("/cp/filter-test", "")));
            assertRejected(blocking, foreignHost(HttpRequest.GET("/cp")));
            assertRejected(blocking, foreignHost(HttpRequest.GET("/cp/filter-test")));
            assertEquals(HttpStatus.OK, blocking.exchange(controlPanelRequest(HttpRequest.POST("/cp/filter-test", ""))).status());

            assertEquals(HttpStatus.OK, blocking.exchange(crossSite(HttpRequest.POST("/filter-test", ""))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(foreignHost(HttpRequest.GET("/filter-test"))).status());

            client.close();
        }
    }

    @Test
    void filtersApplyWhenTheControlPanelPathStartsWithTheContextPath() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties(Map.of(
            "micronaut.server.context-path", "/app",
            ControlPanelModuleConfiguration.PROPERTY_PATH, "/application"
        )))) {
            HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
            BlockingHttpClient blocking = client.toBlocking();

            assertRejected(blocking, crossSite(HttpRequest.POST("/app/application/filter-test", "")));
            assertRejected(blocking, foreignHost(HttpRequest.GET("/app/application")));
            assertEquals(HttpStatus.OK, blocking.exchange(controlPanelRequest(HttpRequest.POST("/app/application/filter-test", ""))).status());

            assertEquals(HttpStatus.OK, blocking.exchange(crossSite(HttpRequest.POST("/app/filter-test", ""))).status());
            assertEquals(HttpStatus.OK, blocking.exchange(foreignHost(HttpRequest.GET("/app/filter-test"))).status());

            client.close();
        }
    }

    private static Map<String, Object> properties(Map<String, Object> extra) {
        Map<String, Object> properties = new HashMap<>(extra);
        properties.put("spec.name", SPEC_NAME);
        properties.put(ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS");
        return properties;
    }

    private static MutableHttpRequest<?> crossSite(MutableHttpRequest<?> request) {
        // Only Fetch Metadata: a foreign Origin header is rejected by Micronaut's own CORS
        // drive-by-localhost protection for every route, which would hide what these filters do.
        return request.header("Sec-Fetch-Site", "cross-site");
    }

    private static MutableHttpRequest<?> foreignHost(MutableHttpRequest<?> request) {
        return request.header(HttpHeaders.HOST, FOREIGN_HOST);
    }

    private static MutableHttpRequest<?> controlPanelRequest(MutableHttpRequest<?> request) {
        return request.header(ControlPanelSecurityPaths.REQUESTED_WITH_HEADER, ControlPanelSecurityPaths.REQUESTED_WITH_VALUE);
    }

    private static void assertRejected(BlockingHttpClient client, MutableHttpRequest<?> request) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.exchange(request));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Controller
    static class FilterTestController {

        @Get("/filter-test")
        String get() {
            return "application";
        }

        @Post("/filter-test")
        String post() {
            return "application";
        }

        @Post("/control-panel-other")
        String postOther() {
            return "application";
        }

        @Get("/control-panel-other")
        String getOther() {
            return "application";
        }

    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Controller(ControlPanelSecurityPaths.CONTROL_PANEL)
    static class ControlPanelTestController {

        @Get("/filter-test")
        String get() {
            return "control panel";
        }

        @Post("/filter-test")
        String post() {
            return "control panel";
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = ControlPanelModuleConfiguration.PROPERTY_PATH)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Controller(ControlPanelModuleConfiguration.DEFAULT_PATH)
    static class DefaultPathTestController {

        @Get("/filter-test")
        String get() {
            return "application";
        }

        @Post("/filter-test")
        String post() {
            return "application";
        }
    }
}
