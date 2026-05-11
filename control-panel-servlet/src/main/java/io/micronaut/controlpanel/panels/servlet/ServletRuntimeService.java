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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContext;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts read-only Servlet runtime metadata for the control panel.
 */
@Singleton
@Requires(classes = ServletContext.class)
@Requires(beans = ServletContext.class)
public class ServletRuntimeService {

    private static final String UNKNOWN = "unknown";
    private static final List<String> SAFE_CONFIGURATION_KEYS = List.of(
        "micronaut.server.host",
        "micronaut.server.port",
        "micronaut.server.context-path",
        "micronaut.server.thread-selection",
        "micronaut.server.jetty.access-logger.enabled",
        "micronaut.server.jetty.access-logger.format",
        "micronaut.server.tomcat.access-logger.enabled",
        "micronaut.server.tomcat.access-logger.pattern",
        "micronaut.server.tomcat.access-logger.directory",
        "micronaut.server.undertow.access-logger.enabled",
        "micronaut.server.undertow.access-logger.pattern",
        "micronaut.server.jdk.thread-selection"
    );

    private final ServletContext servletContext;
    private final Environment environment;
    @Nullable
    private final EmbeddedServer embeddedServer;

    /**
     * Constructor.
     *
     * @param servletContext servlet context
     * @param environment application environment
     * @param embeddedServer embedded server, if available
     */
    public ServletRuntimeService(ServletContext servletContext,
                                  Environment environment,
                                  @Nullable EmbeddedServer embeddedServer) {
        this.servletContext = servletContext;
        this.environment = environment;
        this.embeddedServer = embeddedServer;
    }

    /**
     * Builds the body model for rendering.
     *
     * @return servlet runtime body
     */
    public ServletRuntimeBody getBody() {
        var servlets = servletRegistrations(servletContext);
        var filters = filterRegistrations(servletContext);
        var warnings = warnings(servlets, filters);
        return new ServletRuntimeBody(
            true,
            runtimeName(),
            embeddedServer == null ? "external WAR or servlet container" : "embedded",
            contextInfo(servletContext),
            servlets,
            filters,
            runtimeConfig(),
            warnings
        );
    }

    private ServletContextInfo contextInfo(ServletContext context) {
        return new ServletContextInfo(
            text(context.getContextPath(), "/"),
            text(safe(context::getServerInfo), UNKNOWN),
            context.getMajorVersion() + "." + context.getMinorVersion(),
            context.getEffectiveMajorVersion() + "." + context.getEffectiveMinorVersion(),
            text(safe(context::getVirtualServerName), UNKNOWN)
        );
    }

    private List<ServletRegistrationInfo> servletRegistrations(ServletContext context) {
        return context.getServletRegistrations()
            .values()
            .stream()
            .map(registration -> new ServletRegistrationInfo(
                text(registration.getName(), "(unnamed)"),
                text(registration.getClassName(), UNKNOWN),
                sorted(registration.getMappings()),
                text(safe(registration::getRunAsRole), UNKNOWN),
                initParameters(registration.getInitParameters())
            ))
            .sorted(Comparator.comparing(ServletRegistrationInfo::name))
            .toList();
    }

    private List<FilterRegistrationInfo> filterRegistrations(ServletContext context) {
        return context.getFilterRegistrations()
            .values()
            .stream()
            .map(registration -> new FilterRegistrationInfo(
                text(registration.getName(), "(unnamed)"),
                text(registration.getClassName(), UNKNOWN),
                sorted(registration.getUrlPatternMappings()),
                sorted(registration.getServletNameMappings()),
                initParameters(registration.getInitParameters())
            ))
            .sorted(Comparator.comparing(FilterRegistrationInfo::name))
            .toList();
    }

    private InitParameterSummary initParameters(Map<String, String> initParameters) {
        return new InitParameterSummary(initParameters.size(), initParameters.keySet().stream().sorted().toList());
    }

    private List<RuntimeConfigInfo> runtimeConfig() {
        List<RuntimeConfigInfo> values = new ArrayList<>();
        if (embeddedServer != null) {
            values.add(new RuntimeConfigInfo("Active server", embeddedServer.getClass().getName(), "EmbeddedServer"));
            URI uri = safe(embeddedServer::getURI);
            if (uri != null) {
                values.add(new RuntimeConfigInfo("Server URI", uri.toString(), "EmbeddedServer"));
            }
        }
        for (String key : SAFE_CONFIGURATION_KEYS) {
            Optional<String> value = environment.getProperty(key, String.class);
            value.ifPresent(v -> values.add(new RuntimeConfigInfo(key, v, "configuration")));
        }
        if (values.isEmpty()) {
            values.add(new RuntimeConfigInfo("Runtime configuration", "No selected Servlet runtime configuration properties are available.", "configuration"));
        }
        return values;
    }

    private List<DiagnosticWarning> warnings(List<ServletRegistrationInfo> servlets, List<FilterRegistrationInfo> filters) {
        List<DiagnosticWarning> warnings = new ArrayList<>();
        long runtimes = availableRuntimes();
        if (runtimes > 1) {
            warnings.add(new DiagnosticWarning("multiple-runtimes", "warning",
                "Multiple Micronaut Servlet runtimes appear to be on the classpath; the active runtime is " + runtimeName() + "."));
        }
        servlets.stream()
            .filter(servlet -> servlet.mappings().isEmpty())
            .map(servlet -> new DiagnosticWarning("servlet-without-mapping", "info",
                "Servlet '" + servlet.name() + "' is registered without URL mappings."))
            .forEach(warnings::add);
        filters.stream()
            .filter(filter -> filter.urlPatternMappings().isEmpty() && filter.servletNameMappings().isEmpty())
            .map(filter -> new DiagnosticWarning("filter-without-mapping", "info",
                "Filter '" + filter.name() + "' is registered without URL pattern or servlet-name mappings."))
            .forEach(warnings::add);
        return warnings;
    }

    private String runtimeName() {
        if (embeddedServer != null) {
            String className = embeddedServer.getClass().getName().toLowerCase();
            if (className.contains("jetty")) {
                return "Jetty";
            }
            if (className.contains("tomcat")) {
                return "Tomcat";
            }
            if (className.contains("undertow")) {
                return "Undertow";
            }
            if (className.contains("jdk")) {
                return "JDK HTTP server";
            }
        }
        String serverInfo = safe(servletContext::getServerInfo);
        if (serverInfo != null) {
            String lower = serverInfo.toLowerCase();
            if (lower.contains("jetty")) {
                return "Jetty";
            }
            if (lower.contains("tomcat")) {
                return "Tomcat";
            }
            if (lower.contains("undertow")) {
                return "Undertow";
            }
        }
        return UNKNOWN;
    }

    private long availableRuntimes() {
        ClassLoader classLoader = getClass().getClassLoader();
        return List.of(
                "io.micronaut.servlet.jetty.JettyServer",
                "io.micronaut.servlet.tomcat.TomcatServer",
                "io.micronaut.servlet.undertow.UndertowServer",
                "io.micronaut.servlet.jdk.JdkHttpServer"
            )
            .stream()
            .filter(type -> ClassUtils.isPresent(type, classLoader))
            .count();
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }

    private static String text(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Nullable
    private static <T> T safe(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
