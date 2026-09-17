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
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class ServletControlPanelTest {

    private static final String AUDIT_FILTER_NAME = "AuditFilter";
    private static final String AUDIT_FILTER_URL_PATTERN = "/api/*";
    private static final String AUDIT_FILTER_SERVLET_NAME = "MicronautServlet";

    @Test
    void serviceShowsUnavailableStateWithoutServletContext() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ServletRuntimeService service = new ServletRuntimeService(null, ctx.getEnvironment(), null);
            ServletRuntimeBody body = service.getBody();
            assertFalse(body.available());
            assertTrue(body.warnings().stream().anyMatch(warning -> warning.code().equals("servlet-context-unavailable")));
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.builder()
            .properties(Map.of(ServletControlPanel.ENABLED_PROPERTY, false))
            .singletons(servletContext())
            .start()) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(ServletControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertTrue(ctx.containsBean(ServletRuntimeService.class));
            assertFalse(ctx.containsBean(ServletControlPanel.class));
        }
    }

    @Test
    void readsWebFilterMappingsAndRedactsInitParameterValues() {
        try (ApplicationContext ctx = ApplicationContext.builder()
            .properties(Map.of("micronaut.server.port", 8081))
            .singletons(servletContext())
            .start()) {
            ServletControlPanel panel = ctx.getBean(ServletControlPanel.class);

            ServletRuntimeBody body = panel.getBody();

            assertEquals("Servlet Runtime", panel.getTitle());
            assertEquals("fa-server", panel.getIcon());
            assertEquals("Jetty", body.runtimeName());
            assertEquals("/demo", body.context().contextPath());
            assertEquals("6.1", body.context().servletApiVersion());
            assertEquals(List.of("/"), body.servlets().get(0).mappings());
            assertEquals(List.of(AUDIT_FILTER_URL_PATTERN), body.filters().get(0).urlPatternMappings());
            assertEquals(List.of(AUDIT_FILTER_SERVLET_NAME), body.filters().get(0).servletNameMappings());
            assertEquals(List.of("apiKey"), body.filters().get(0).initParameters().names());
            assertFalse(body.toString().contains("super-secret-value"));
            assertTrue(body.config().stream().anyMatch(config -> config.label().equals("micronaut.server.port") && config.value().equals("8081")));
        }
    }

    @Test
    void detectsRuntimeFromServletContextServerInfo() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            assertEquals("Tomcat", new ServletRuntimeService(servletContext("Apache Tomcat/10.1.20"), ctx.getEnvironment(), null).getBody().runtimeName());
            assertEquals("Undertow", new ServletRuntimeService(servletContext("Undertow/2.3.10"), ctx.getEnvironment(), null).getBody().runtimeName());
            assertEquals("unknown", new ServletRuntimeService(servletContext("ExampleServer/1.0"), ctx.getEnvironment(), null).getBody().runtimeName());
        }
    }

    @Test
    void sortsRegistrationsAndWarnsWhenMappingsAreMissing() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ServletContext context = mock(ServletContext.class);
            ServletRegistration zServlet = servlet("ZServlet", List.of("/z"));
            ServletRegistration aServlet = servlet("AServlet", List.of());
            FilterRegistration zFilter = filter("ZFilter", List.of("/z/*"), List.of());
            FilterRegistration aFilter = filter("AFilter", List.of(), List.of());

            basicContext(context, "Jetty(12.0.0)");
            doReturn(Map.of("ZServlet", zServlet, "AServlet", aServlet)).when(context).getServletRegistrations();
            doReturn(Map.of("ZFilter", zFilter, "AFilter", aFilter)).when(context).getFilterRegistrations();

            ServletRuntimeBody body = new ServletRuntimeService(context, ctx.getEnvironment(), null).getBody();

            assertEquals(List.of("AServlet", "ZServlet"), body.servlets().stream().map(ServletRegistrationInfo::name).toList());
            assertEquals(List.of("AFilter", "ZFilter"), body.filters().stream().map(FilterRegistrationInfo::name).toList());
            assertTrue(body.warnings().stream().anyMatch(warning -> warning.code().equals("servlet-without-mapping")));
            assertTrue(body.warnings().stream().anyMatch(warning -> warning.code().equals("filter-without-mapping")));
        }
    }

    @Test
    void usesSafeFallbacksForUnavailableServletContextMetadata() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ServletContext context = servletContext("Jetty(12.0.0)");
            when(context.getContextPath()).thenReturn(" ");
            doThrow(IllegalStateException.class).when(context).getServerInfo();
            doThrow(IllegalStateException.class).when(context).getVirtualServerName();

            ServletRuntimeBody body = new ServletRuntimeService(context, ctx.getEnvironment(), null).getBody();

            assertEquals("/", body.context().contextPath());
            assertEquals("unknown", body.context().serverInfo());
            assertEquals("unknown", body.context().virtualServerName());
            assertEquals("unknown", body.runtimeName());
        }
    }

    @Test
    void includesEmbeddedServerRuntimeConfigurationWhenAvailable() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            EmbeddedServer server = mock(EmbeddedServer.class);
            when(server.getURI()).thenReturn(URI.create("http://localhost:8080"));

            ServletRuntimeBody body = new ServletRuntimeService(null, ctx.getEnvironment(), server).getBody();

            assertFalse(body.available());
            assertEquals("embedded", body.runtimeMode());
            assertTrue(body.config().stream().anyMatch(config -> config.label().equals("Active server")));
            assertTrue(body.config().stream().anyMatch(config -> config.label().equals("Server URI") && config.value().equals("http://localhost:8080")));
        }
    }

    @Test
    void templateShowsMappingsAndRedactionLabel() throws IOException {
        try (var in = getClass().getResourceAsStream("/views/servlet-runtime/detail.hbs")) {
            assertNotNull(in);
            var template = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("URL patterns"));
            assertTrue(template.contains("Servlet mappings"));
            assertTrue(template.contains("values redacted"));
            assertFalse(template.contains("getInitParameter"));
        }
    }

    private static ServletContext servletContext() {
        return servletContext("Jetty(12.0.0)");
    }

    private static ServletContext servletContext(String serverInfo) {
        ServletContext context = mock(ServletContext.class);
        ServletRegistration servlet = servlet("MicronautServlet", List.of("/"));
        FilterRegistration filter = filter(AUDIT_FILTER_NAME, List.of(AUDIT_FILTER_URL_PATTERN), List.of(AUDIT_FILTER_SERVLET_NAME));

        basicContext(context, serverInfo);
        doReturn(Map.of("MicronautServlet", servlet)).when(context).getServletRegistrations();
        doReturn(Map.of("AuditFilter", filter)).when(context).getFilterRegistrations();
        return context;
    }

    private static void basicContext(ServletContext context, String serverInfo) {
        when(context.getContextPath()).thenReturn("/demo");
        when(context.getServerInfo()).thenReturn(serverInfo);
        when(context.getMajorVersion()).thenReturn(6);
        when(context.getMinorVersion()).thenReturn(1);
        when(context.getEffectiveMajorVersion()).thenReturn(6);
        when(context.getEffectiveMinorVersion()).thenReturn(1);
        when(context.getVirtualServerName()).thenReturn("default");
    }

    private static ServletRegistration servlet(String name, List<String> mappings) {
        ServletRegistration servlet = mock(ServletRegistration.class);
        when(servlet.getName()).thenReturn(name);
        when(servlet.getClassName()).thenReturn("io.micronaut.servlet.engine.DefaultMicronautServlet");
        when(servlet.getMappings()).thenReturn(mappings);
        when(servlet.getRunAsRole()).thenReturn(null);
        when(servlet.getInitParameters()).thenReturn(Map.of("startupMode", "super-secret-value"));
        return servlet;
    }

    private static FilterRegistration filter(String name, List<String> urlPatternMappings, List<String> servletNameMappings) {
        FilterRegistration filter = mock(FilterRegistration.class);
        when(filter.getName()).thenReturn(name);
        when(filter.getClassName()).thenReturn(AuditFilter.class.getName());
        when(filter.getUrlPatternMappings()).thenReturn(urlPatternMappings);
        when(filter.getServletNameMappings()).thenReturn(servletNameMappings);
        when(filter.getInitParameters()).thenReturn(Map.of("apiKey", "super-secret-value"));
        return filter;
    }

    private static final class AuditFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
