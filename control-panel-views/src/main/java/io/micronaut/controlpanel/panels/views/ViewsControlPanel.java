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
package io.micronaut.controlpanel.panels.views;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.views.ModelAndView;
import io.micronaut.views.ViewsConfiguration;
import io.micronaut.views.ViewsRenderer;
import io.micronaut.views.csp.CspConfiguration;
import io.micronaut.views.http.ViewsFilterConfiguration;
import io.micronaut.views.model.ViewModelProcessor;
import io.micronaut.views.model.security.CsrfViewModelProcessorConfiguration;
import io.micronaut.views.model.security.SecurityViewModelProcessorConfiguration;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Control panel for Micronaut Views runtime diagnostics.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Singleton
@Requires(classes = ViewsRenderer.class)
@Requires(property = ViewsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class ViewsControlPanel extends AbstractControlPanel<ViewsControlPanel.Body> {

    /**
     * Panel name.
     */
    public static final String NAME = "views";
    /**
     * Panel enabled property.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    /**
     * Web category used by Views diagnostics.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("web", "Web", "fas fa-globe");
    private static final String VIEW_ANNOTATION = "io.micronaut.views.View";
    // Reuses badge/body diagnostics during one dashboard render without hiding live changes for long.
    private static final long BODY_CACHE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private final Router router;
    private final Environment environment;
    private final Optional<ViewsConfiguration> viewsConfiguration;
    private final Optional<ViewsFilterConfiguration> viewsFilterConfiguration;
    private final Optional<CspConfiguration> cspConfiguration;
    private final Optional<SecurityViewModelProcessorConfiguration> securityViewModelProcessorConfiguration;
    private final Optional<CsrfViewModelProcessorConfiguration> csrfViewModelProcessorConfiguration;
    private final Collection<ViewModelProcessor<?, ?>> viewModelProcessors;
    private final List<ActiveRenderer> renderers;
    private final AtomicReference<CachedBody> cachedBody = new AtomicReference<>();

    /**
     * Constructor.
     *
     * @param router the Micronaut router
     * @param environment the Micronaut environment
     * @param viewsConfiguration optional global Views configuration
     * @param viewsFilterConfiguration optional Views filter configuration
     * @param cspConfiguration optional CSP configuration
     * @param securityViewModelProcessorConfiguration optional security model processor configuration
     * @param csrfViewModelProcessorConfiguration optional CSRF model processor configuration
     * @param viewModelProcessors view model processor beans
     * @param renderers active Views renderer beans
     * @param configuration the control panel configuration
     */
    public ViewsControlPanel(Router router,
                             Environment environment,
                             Optional<ViewsConfiguration> viewsConfiguration,
                             Optional<ViewsFilterConfiguration> viewsFilterConfiguration,
                             Optional<CspConfiguration> cspConfiguration,
                             Optional<SecurityViewModelProcessorConfiguration> securityViewModelProcessorConfiguration,
                             Optional<CsrfViewModelProcessorConfiguration> csrfViewModelProcessorConfiguration,
                             Collection<ViewModelProcessor<?, ?>> viewModelProcessors,
                             Collection<ViewsRenderer<?, ?>> renderers,
                             @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.router = router;
        this.environment = environment;
        this.viewsConfiguration = viewsConfiguration;
        this.viewsFilterConfiguration = viewsFilterConfiguration;
        this.cspConfiguration = cspConfiguration;
        this.securityViewModelProcessorConfiguration = securityViewModelProcessorConfiguration;
        this.csrfViewModelProcessorConfiguration = csrfViewModelProcessorConfiguration;
        this.viewModelProcessors = List.copyOf(viewModelProcessors);
        this.renderers = renderers.stream()
            .sorted(Comparator.<ViewsRenderer<?, ?>>comparingInt(ViewsRenderer::getOrder)
                .thenComparing(r -> r.getClass().getName()))
            .map(renderer -> new ActiveRenderer(toRendererRow(renderer), renderer))
            .toList();
    }

    @Override
    public Body getBody() {
        long now = System.nanoTime();
        CachedBody cached = cachedBody.get();
        if (cached != null && now - cached.createdAtNanos() <= BODY_CACHE_NANOS) {
            return cached.body();
        }
        Body body = buildBody();
        cachedBody.set(new CachedBody(System.nanoTime(), body));
        return body;
    }

    private Body buildBody() {
        List<WarningRow> warnings = new ArrayList<>();
        ConfigurationBody configuration = resolveConfiguration(warnings);
        List<RouteRow> routes = resolveRoutes(warnings);
        ProcessorBody processors = resolveProcessors();
        return new Body(configuration, renderers.stream().map(ActiveRenderer::row).toList(), routes, processors, List.copyOf(warnings));
    }

    @Override
    public String getBadge() {
        Body body = getBody();
        if (body.warningCount() == 0) {
            return body.routeCount() + " routes";
        }
        return body.routeCount() + " routes, " + body.warningCount() + " "
            + (body.warningCount() == 1 ? "warning" : "warnings");
    }

    @Override
    public Category getCategory() {
        return CATEGORY;
    }

    private ConfigurationBody resolveConfiguration(List<WarningRow> warnings) {
        Optional<Boolean> configuredEnabled = environment.getProperty("micronaut.views.enabled", Boolean.class);
        boolean viewsAvailable = viewsConfiguration.isPresent() || configuredEnabled.isPresent();
        boolean viewsEnabled = viewsConfiguration.map(ViewsConfiguration::isEnabled).orElseGet(() -> configuredEnabled.orElse(true));
        if (!viewsEnabled) {
            warnings.add(new WarningRow("Views disabled", "micronaut.views.enabled is false."));
        }
        String folder = viewsConfiguration.map(ViewsConfiguration::getFolder)
            .or(() -> environment.getProperty("micronaut.views.folder", String.class))
            .filter(StringUtils::isNotEmpty)
            .orElse("views");
        return new ConfigurationBody(
            viewsAvailable,
            viewsEnabled,
            folder,
            viewsFilterConfiguration.map(ViewsFilterConfiguration::isEnabled).map(ViewsControlPanel::enabledLabel).orElse("unavailable"),
            cspConfiguration.isPresent(),
            cspConfiguration.map(CspConfiguration::isEnabled).map(ViewsControlPanel::enabledLabel).orElse("unavailable"),
            cspConfiguration.map(CspConfiguration::isReportOnly).map(ViewsControlPanel::enabledLabel).orElse("unavailable"),
            cspConfiguration.map(CspConfiguration::isNonceEnabled).map(ViewsControlPanel::enabledLabel).orElse("unavailable"),
            cspConfiguration.map(CspConfiguration::getFilterPath).filter(StringUtils::isNotEmpty).orElse("unavailable")
        );
    }

    private List<RouteRow> resolveRoutes(List<WarningRow> warnings) {
        Map<String, TemplateProbe> probeCache = new LinkedHashMap<>();
        return router.uriRoutes()
            .distinct()
            .filter(ViewsControlPanel::isViewRoute)
            .sorted(Comparator.comparing((UriRouteInfo<?, ?> r) -> r.getUriMatchTemplate().toPathString())
                .thenComparing(UriRouteInfo::getHttpMethodName))
            .map(route -> toRouteRow(route, probeCache, warnings))
            .toList();
    }

    private static boolean isViewRoute(UriRouteInfo<?, ?> route) {
        return route.getTargetMethod().stringValue(VIEW_ANNOTATION).isPresent()
            || ModelAndView.class.isAssignableFrom(route.getTargetMethod().getReturnType().getType());
    }

    private RouteRow toRouteRow(UriRouteInfo<?, ?> route,
                                Map<String, TemplateProbe> probeCache,
                                List<WarningRow> warnings) {
        Optional<String> staticView = route.getTargetMethod().stringValue(VIEW_ANNOTATION).filter(StringUtils::isNotEmpty);
        boolean dynamicModelAndView = staticView.isEmpty()
            && ModelAndView.class.isAssignableFrom(route.getTargetMethod().getReturnType().getType());
        TemplateStatus status = staticView
            .map(view -> resolveTemplateStatus(route, view, probeCache, warnings))
            .orElse(dynamicModelAndView ? TemplateStatus.DYNAMIC : TemplateStatus.UNKNOWN);
        String targetMethod = route.getTargetMethod().getDeclaringType().getName() + "#" + route.getTargetMethod().getName();
        return new RouteRow(
            route.getHttpMethodName(),
            route.getUriMatchTemplate().toPathString(),
            targetMethod,
            staticView.orElse(dynamicModelAndView ? "dynamic ModelAndView" : "unknown"),
            dynamicModelAndView,
            mediaTypes(route.getProduces()),
            status.name(),
            status.label,
            status.badgeClass,
            status.warning
        );
    }

    private TemplateStatus resolveTemplateStatus(UriRouteInfo<?, ?> route,
                                                 String view,
                                                 Map<String, TemplateProbe> probeCache,
                                                 List<WarningRow> warnings) {
        if (renderers.isEmpty()) {
            addRouteWarning(warnings, "No Views renderer", route, view, "No active ViewsRenderer bean can check or render this static @View route.");
            return TemplateStatus.NO_RENDERER;
        }
        boolean failed = false;
        for (ActiveRenderer renderer : renderers) {
            TemplateProbe probe = probeCache.computeIfAbsent(renderer.row().className() + ":" + view,
                key -> probe(renderer.renderer(), view));
            if (probe.exists()) {
                return TemplateStatus.FOUND;
            }
            failed = failed || probe.failed();
        }
        if (failed) {
            addRouteWarning(warnings, "Template check failed", route, view, "At least one renderer failed during ViewsRenderer.exists.");
            return TemplateStatus.CHECK_FAILED;
        }
        addRouteWarning(warnings, "Missing template", route, view, "No active ViewsRenderer reported this static @View template as present.");
        return TemplateStatus.MISSING_TEMPLATE;
    }

    private static TemplateProbe probe(ViewsRenderer<?, ?> renderer, String view) {
        try {
            return new TemplateProbe(renderer.exists(view), false);
        } catch (RuntimeException e) {
            return new TemplateProbe(false, true);
        }
    }

    private static void addRouteWarning(List<WarningRow> warnings,
                                        String title,
                                        UriRouteInfo<?, ?> route,
                                        String view,
                                        String message) {
        warnings.add(new WarningRow(title, route.getHttpMethodName() + " " + route.getUriMatchTemplate().toPathString()
            + " -> " + view + ": " + message));
    }

    private ProcessorBody resolveProcessors() {
        return new ProcessorBody(
            viewModelProcessors.size(),
            securityViewModelProcessorConfiguration.map(SecurityViewModelProcessorConfiguration::isEnabled).map(ViewsControlPanel::enabledLabel).orElse("unavailable"),
            securityViewModelProcessorConfiguration.map(SecurityViewModelProcessorConfiguration::getSecurityKey).orElse("unavailable"),
            securityViewModelProcessorConfiguration.map(SecurityViewModelProcessorConfiguration::getPrincipalNameKey).orElse("unavailable"),
            securityViewModelProcessorConfiguration.map(SecurityViewModelProcessorConfiguration::getAttributesKey).orElse("unavailable"),
            csrfViewModelProcessorConfiguration.map(CsrfViewModelProcessorConfiguration::isEnabled).map(ViewsControlPanel::enabledLabel).orElse("unavailable"),
            csrfViewModelProcessorConfiguration.map(CsrfViewModelProcessorConfiguration::getCsrfTokenKey).orElse("unavailable")
        );
    }

    private static RendererRow toRendererRow(ViewsRenderer<?, ?> renderer) {
        Class<?> rendererClass = renderer.getClass();
        return new RendererRow(rendererClass.getName(), inferEngineName(rendererClass.getName()), renderer.getOrder(), produces(rendererClass));
    }

    private static String inferEngineName(String className) {
        String lower = className.toLowerCase(Locale.ROOT);
        String[] engines = { "thymeleaf", "handlebars", "velocity", "freemarker", "rocker", "soy", "pebble", "jte", "jstachio", "react" };
        for (String engine : engines) {
            if (lower.contains(engine)) {
                return engine;
            }
        }
        return "generic";
    }

    private static List<String> mediaTypes(List<MediaType> mediaTypes) {
        if (mediaTypes == null || mediaTypes.isEmpty()) {
            return List.of("not declared");
        }
        return mediaTypes.stream().map(MediaType::toString).toList();
    }

    private static List<String> produces(Class<?> rendererClass) {
        Produces produces = rendererClass.getAnnotation(Produces.class);
        if (produces == null || produces.value().length == 0) {
            return List.of("not declared");
        }
        return List.of(produces.value());
    }

    private static String enabledLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private enum TemplateStatus {
        FOUND("found", "badge-success", false),
        MISSING_TEMPLATE("missing template", "badge-danger", true),
        NO_RENDERER("no renderer", "badge-danger", true),
        CHECK_FAILED("check failed", "badge-warning", true),
        DYNAMIC("dynamic", "badge-secondary", false),
        UNKNOWN("unknown", "badge-secondary", false);

        private final String label;
        private final String badgeClass;
        private final boolean warning;

        TemplateStatus(String label, String badgeClass, boolean warning) {
            this.label = label;
            this.badgeClass = badgeClass;
            this.warning = warning;
        }
    }

    private record TemplateProbe(boolean exists, boolean failed) {
    }

    private record ActiveRenderer(RendererRow row, ViewsRenderer<?, ?> renderer) {
    }

    private record CachedBody(long createdAtNanos, Body body) {
    }

    /**
     * Body model.
     *
     * @param configuration global Views configuration
     * @param renderers active renderer rows
     * @param routes view route rows
     * @param processors model processor state
     * @param warnings warnings generated by diagnostics
     */
    @ReflectiveAccess
    public record Body(ConfigurationBody configuration,
                       List<RendererRow> renderers,
                       List<RouteRow> routes,
                       ProcessorBody processors,
                       List<WarningRow> warnings) {
        /**
         * Route count.
         *
         * @return view route count
         */
        public int routeCount() {
            return routes.size();
        }

        /**
         * Renderer count.
         *
         * @return active renderer count
         */
        public int rendererCount() {
            return renderers.size();
        }

        /**
         * Warning count.
         *
         * @return warning count
         */
        public int warningCount() {
            return warnings.size();
        }
    }

    /**
     * Global Views configuration model.
     *
     * @param viewsConfigurationAvailable whether a configuration bean or explicit enabled property was detected
     * @param viewsEnabled whether Views are enabled
     * @param folder configured Views folder
     * @param filterEnabled Views filter state
     * @param cspAvailable whether CSP configuration is available
     * @param cspEnabled CSP enabled state
     * @param cspReportOnly CSP report-only state
     * @param cspNonce CSP nonce generation state
     * @param cspFilterPath CSP filter path
     */
    @ReflectiveAccess
    public record ConfigurationBody(boolean viewsConfigurationAvailable,
                                    boolean viewsEnabled,
                                    String folder,
                                    String filterEnabled,
                                    boolean cspAvailable,
                                    String cspEnabled,
                                    String cspReportOnly,
                                    String cspNonce,
                                    String cspFilterPath) {
    }

    /**
     * Renderer row model.
     *
     * @param className renderer class name
     * @param engine inferred engine name
     * @param order renderer order
     * @param produces produced media types declared on the renderer class
     */
    @ReflectiveAccess
    public record RendererRow(String className,
                              String engine,
                              int order,
                              List<String> produces) {
    }

    /**
     * Route row model.
     *
     * @param method HTTP method
     * @param uri route URI template
     * @param targetMethod controller target method
     * @param viewName static view name or dynamic marker
     * @param dynamic whether the route has a dynamic ModelAndView view name
     * @param produces produced media types
     * @param status internal template status
     * @param statusLabel display status
     * @param statusBadgeClass badge class
     * @param warning whether the row has a warning status
     */
    @ReflectiveAccess
    public record RouteRow(String method,
                           String uri,
                           String targetMethod,
                           String viewName,
                           boolean dynamic,
                           List<String> produces,
                           String status,
                           String statusLabel,
                           String statusBadgeClass,
                           boolean warning) {
    }

    /**
     * Model processor diagnostics.
     *
     * @param processorCount number of processor beans
     * @param securityEnabled security processor enabled state
     * @param securityKey configured security model key
     * @param principalNameKey configured principal name key
     * @param attributesKey configured attributes key
     * @param csrfEnabled CSRF processor enabled state
     * @param csrfTokenKey configured CSRF token key
     */
    @ReflectiveAccess
    public record ProcessorBody(int processorCount,
                                String securityEnabled,
                                String securityKey,
                                String principalNameKey,
                                String attributesKey,
                                String csrfEnabled,
                                String csrfTokenKey) {
    }

    /**
     * Warning row model.
     *
     * @param title warning title
     * @param message warning detail
     */
    @ReflectiveAccess
    public record WarningRow(String title, String message) {
    }
}
