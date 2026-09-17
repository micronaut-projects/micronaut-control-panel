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
package io.micronaut.controlpanel.panels.servlet;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.servlet.api.annotation.ServletFilterBean;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServletControlPanelJettyIntegrationTest {

    @Test
    void rendersServletRuntimePanelFromJettyServletContext() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "micronaut.server.port", -1,
            ControlPanelSecurityConfiguration.PROPERTY_ACCESS, "ANONYMOUS"
        ));
        try {
            var client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
            assertTrue(server.getApplicationContext().containsBean(ServletControlPanel.class));

            String html = client.retrieve(ControlPanelModuleConfiguration.DEFAULT_PATH + "/" + ServletControlPanel.NAME);

            assertTrue(html.contains("Servlet Runtime"));
            assertTrue(html.contains("Jetty"));
            assertTrue(html.contains("Servlet API"));
            assertTrue(html.contains("RenderedAuditFilter"));
            assertTrue(html.contains("/api/*"));
            assertTrue(html.contains("MicronautServlet"));
        } finally {
            server.stop();
        }
    }

    @Factory
    static final class FilterFactory {
        @ServletFilterBean(
            filterName = "RenderedAuditFilter",
            urlPatterns = "/api/*",
            servletNames = "MicronautServlet"
        )
        Filter renderedAuditFilter() {
            return new RenderedAuditFilter();
        }
    }

    private static final class RenderedAuditFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
