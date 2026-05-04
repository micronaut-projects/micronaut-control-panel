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
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

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

    private static final Function<UriRouteInfo<?, ?>, String> KEY_MAPPER =
        r -> r.getTargetMethod().getDeclaringType().getName();

    private static final Comparator<UriRouteInfo<?, ?>> COMPARATOR_BY_URI =
        Comparator.comparing(r -> r.getUriMatchTemplate().toPathString());

    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final String MERGED_GET_HEAD = "GET, HEAD";

    private static final Predicate<UriRouteInfo<?, ?>> IS_MICRONAUT_ROUTE =
        r -> {
            Package p = r.getTargetMethod().getDeclaringType().getPackage();
            return p != null && p.getName().startsWith("io.micronaut");
        };

    private final Body body;

    public RoutesControlPanel(Router router,

                              @Named(NAME) ControlPanelConfiguration configuration,
                              Environment environment) {
        super(NAME, configuration);
        var appRoutes = computeRoutes(router, IS_MICRONAUT_ROUTE.negate());
        var micronautRoutes = computeRoutes(router, IS_MICRONAUT_ROUTE);
        int totalAppRoutes = appRoutes.values().stream().mapToInt(List::size).sum();
        int totalMicronautRoutes = micronautRoutes.values().stream().mapToInt(List::size).sum();

        var viewer = resolveOpenApiViewer(environment);

        this.body = new Body(appRoutes, micronautRoutes,
            viewer == null ? null : viewer.uri(),
            viewer == null ? null : viewer.label(),
            totalAppRoutes + totalMicronautRoutes);
    }

    private static Viewer resolveOpenApiViewer(Environment env) {
        final String base = "micronaut.router.static-resources.";
        final String suffix = ".mapping";
        final String[][] viewers = new String[][] {
            { "swagger-ui", "Swagger UI" },
            { "redoc", "ReDoc" },
            { "openapi-explorer", "OpenAPI Explorer" },
            { "scalar", "Scalar" },
            { "rapidoc", "RapiDoc" }
        };
        for (String[] v : viewers) {
            String key = v[0];
            String label = v[1];
            Optional<String> mappingOpt = env.getProperty(base + key + suffix, String.class);
            if (mappingOpt.isPresent()) {
                String mapping = mappingOpt.get();
                if (!mapping.isBlank()) {
                    String basePath = mapping.endsWith("/**") ? mapping.substring(0, mapping.length() - 3) : mapping;
                    return new Viewer(label, basePath);
                }
            }
        }
        return null;
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    private static LinkedHashMap<String, List<RouteRow>> computeRoutes(Router router, Predicate<UriRouteInfo<?, ?>> filter) {
        Map<String, List<UriRouteInfo<?, ?>>> grouped = router.uriRoutes()
            .filter(filter)
            .distinct()
            .sorted(COMPARATOR_BY_URI.thenComparing(UriRouteInfo::getHttpMethodName))
            .collect(Collectors.groupingBy(KEY_MAPPER, LinkedHashMap::new, Collectors.toUnmodifiableList()));

        LinkedHashMap<String, List<RouteRow>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<UriRouteInfo<?, ?>>> e : grouped.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableList(toRows(e.getValue())));
        }
        return result;
    }

    private static List<RouteRow> toRows(List<UriRouteInfo<?, ?>> routes) {
        List<RouteRow> rows = new ArrayList<>(routes.size());
        int i = 0;
        while (i < routes.size()) {
            UriRouteInfo<?, ?> current = routes.get(i);
            String method = current.getHttpMethodName();
            if (isMergeableGetWithNext(current, i, routes)) {
                var tm = current.getTargetMethod();
                rows.add(new RouteRow(MERGED_GET_HEAD,
                    current.getUriMatchTemplate(),
                    current.getProduces(),
                    current.getConsumes(),
                    new TargetMethod(tm.getName(), toStringList(tm.getArguments()))));
                i += 2;
                continue;
            }
            if (!isSkippableHead(current, i, routes)) {
                var tm = current.getTargetMethod();
                rows.add(new RouteRow(method,
                    current.getUriMatchTemplate(),
                    current.getProduces(),
                    current.getConsumes(),
                    new TargetMethod(tm.getName(), toStringList(tm.getArguments()))));
            }
            i++;
        }
        return rows;
    }

    private static boolean isMergeableGetWithNext(UriRouteInfo<?, ?> current, int index, List<UriRouteInfo<?, ?>> routes) {
        if (!METHOD_GET.equals(current.getHttpMethodName())) {
            return false;
        }
        if (index + 1 >= routes.size()) {
            return false;
        }
        UriRouteInfo<?, ?> next = routes.get(index + 1);
        return METHOD_HEAD.equals(next.getHttpMethodName())
            && sameUri(current, next)
            && sameTargetMethodName(current, next)
            && sameProduces(current, next)
            && sameConsumes(current, next);
    }

    private static boolean isSkippableHead(UriRouteInfo<?, ?> current, int index, List<UriRouteInfo<?, ?>> routes) {
        if (!METHOD_HEAD.equals(current.getHttpMethodName()) || index == 0) {
            return false;
        }
        UriRouteInfo<?, ?> prev = routes.get(index - 1);
        return METHOD_GET.equals(prev.getHttpMethodName())
            && sameUri(current, prev)
            && sameTargetMethodName(current, prev)
            && sameProduces(current, prev)
            && sameConsumes(current, prev);
    }

    private static boolean sameUri(UriRouteInfo<?, ?> a, UriRouteInfo<?, ?> b) {
        return a.getUriMatchTemplate().toPathString().equals(b.getUriMatchTemplate().toPathString());
    }

    private static boolean sameTargetMethodName(UriRouteInfo<?, ?> a, UriRouteInfo<?, ?> b) {

        return a.getTargetMethod().getName().equals(b.getTargetMethod().getName());
    }

    private static boolean sameProduces(UriRouteInfo<?, ?> a, UriRouteInfo<?, ?> b) {
        return a.getProduces().equals(b.getProduces());
    }

    private static boolean sameConsumes(UriRouteInfo<?, ?> a, UriRouteInfo<?, ?> b) {
        return a.getConsumes().equals(b.getConsumes());
    }

    private static List<String> toStringList(io.micronaut.core.type.Argument<?>[] args) {
        if (args == null) {
            return List.of();
        }
        return Arrays.stream(args).map(Object::toString).toList();
    }

    private record Viewer(String label, String uri) { }

    /**
     * View model used by templates to render a single route row.
     *
     * @param httpMethodName  HTTP method name or merged value (e.g. "GET, HEAD").
     * @param uriMatchTemplate The URI template for the route.
     * @param produces        Produced media types.
     * @param consumes        Consumed media types.
     * @param targetMethod    Target method info (name, arguments).
     */
    @ReflectiveAccess
    public record RouteRow(
            String httpMethodName,
            UriMatchTemplate uriMatchTemplate,
            List<MediaType> produces,
            List<MediaType> consumes,
            TargetMethod targetMethod
    ) {
        public String getHttpMethodName() {
            return httpMethodName;
        }

        public UriMatchTemplate getUriMatchTemplate() {
            return uriMatchTemplate;
        }

        public List<MediaType> getProduces() {
            return produces;
        }

        public List<MediaType> getConsumes() {
            return consumes;
        }

        public TargetMethod getTargetMethod() {
            return targetMethod;
        }
    }

    /**
     * View model for a controller method target.
     *
     * @param name      Method name.
     * @param arguments Stringified argument list.
     */
    @ReflectiveAccess
    public record TargetMethod(
            String name,
            List<String> arguments
    ) {
        public String getName() {
            return name;
        }

        public List<String> getArguments() {
            return arguments;
        }
    }

    @ReflectiveAccess
    record Body(Map<String, List<RouteRow>> appRoutes,
                Map<String, List<RouteRow>> micronautRoutes,
                String openApiViewerUri,
                String openApiViewerLabel,
                int totalRoutes) { }
}
