/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.controlpanel.core.panels;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Control panel that displays information about the available routes.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Refreshable
@Requires(property = RoutesControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class RoutesControlPanel extends AbstractControlPanel<RoutesControlPanel.Body> {

    public static final String NAME = "routes";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private static final String STATIC_RESOURCES_PREFIX = "micronaut.router.static-resources.";

    private static final Map<String, ViewerConfig> KNOWN_VIEWERS = Map.of(
        "swagger-ui", new ViewerConfig("Swagger UI", "si-swagger"),
        "redoc", new ViewerConfig("ReDoc", "si-readthedocs"),
        "openapi-explorer", new ViewerConfig("OpenAPI Explorer", "si-openapiinitiative"),
        "scalar", new ViewerConfig("Scalar", "si-openapiinitiative"),
        "rapidoc", new ViewerConfig("RapiDoc", "si-openapiinitiative")
    );

    private static final Function<UriRouteInfo<?, ?>, String> KEY_MAPPER =
        r -> r.getTargetMethod().getDeclaringType().getName();

    private static final Comparator<UriRouteInfo<?, ?>> COMPARATOR_BY_URI =
        Comparator.comparing(r -> r.getUriMatchTemplate().toPathString());

    private static final Predicate<UriRouteInfo<?, ?>> IS_MICRONAUT_ROUTE =
        r -> r.getTargetMethod().getDeclaringType().getPackage().getName().startsWith("io.micronaut");

    private final Body body;

    private final String badge;

    public RoutesControlPanel(Router router, Environment environment, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        var appRoutes = computeRoutes(router, IS_MICRONAUT_ROUTE.negate());
        var micronautRoutes = computeRoutes(router, IS_MICRONAUT_ROUTE);
        int totalAppRoutes = appRoutes.values().stream().mapToInt(List::size).sum();
        int totalMicronautRoutes = micronautRoutes.values().stream().mapToInt(List::size).sum();
        var openApiViewers = detectOpenApiViewers(environment);

        this.body = new Body(appRoutes, micronautRoutes, openApiViewers);
        this.badge = String.valueOf(totalAppRoutes + totalMicronautRoutes);
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public String getBadge() {
        return badge;
    }

    private static LinkedHashMap<String, List<UriRouteInfo<?, ?>>> computeRoutes(Router router, Predicate<UriRouteInfo<?, ?>> filter) {
        return router.uriRoutes()
            .filter(filter)
            .distinct()
            .sorted(COMPARATOR_BY_URI.thenComparing(UriRouteInfo::getHttpMethodName))
            .collect(Collectors.groupingBy(KEY_MAPPER, LinkedHashMap::new, Collectors.toUnmodifiableList()));
    }

    private static List<OpenApiViewerLink> detectOpenApiViewers(Environment environment) {
        List<OpenApiViewerLink> viewers = new ArrayList<>();
        for (var entry : KNOWN_VIEWERS.entrySet()) {
            String viewerKey = entry.getKey();
            String mappingProperty = STATIC_RESOURCES_PREFIX + viewerKey + ".mapping";
            environment.getProperty(mappingProperty, String.class).ifPresent(mapping -> {
                String uri = cleanUri(mapping);
                ViewerConfig config = entry.getValue();
                viewers.add(new OpenApiViewerLink(viewerKey, config.label(), config.icon(), uri));
            });
        }
        return List.copyOf(viewers);
    }

    private static String cleanUri(String mapping) {
        return mapping.replaceAll("/\\*+$", "");
    }

    @ReflectiveAccess
    record Body(Map<String, List<UriRouteInfo<?, ?>>> appRoutes, Map<String, List<UriRouteInfo<?, ?>>> micronautRoutes, List<OpenApiViewerLink> openApiViewers) { }

    @ReflectiveAccess
    record OpenApiViewerLink(String name, String label, String icon, String uri) { }

    private record ViewerConfig(String label, String icon) { }
}
