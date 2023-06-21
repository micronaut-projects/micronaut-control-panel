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
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

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

    private static final Function<UriRouteInfo<?, ?>, String> KEY_MAPPER =
        r -> r.getTargetMethod().getDeclaringType().getName();

    private static final Comparator<UriRouteInfo<?, ?>> COMPARATOR_BY_URI =
        Comparator.comparing(r -> r.getUriMatchTemplate().toPathString());

    private static final Predicate<UriRouteInfo<?, ?>> IS_MICRONAUT_ROUTE =
        r -> r.getTargetMethod().getDeclaringType().getPackage().getName().startsWith("io.micronaut");

    private final Body body;

    private final String badge;

    public RoutesControlPanel(Router router, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        var appRoutes = computeRoutes(router, IS_MICRONAUT_ROUTE.negate());
        var micronautRoutes = computeRoutes(router, IS_MICRONAUT_ROUTE);
        int totalAppRoutes = appRoutes.values().stream().mapToInt(List::size).sum();
        int totalMicronautRoutes = micronautRoutes.values().stream().mapToInt(List::size).sum();

        this.body = new Body(appRoutes, micronautRoutes);
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
            .sorted(COMPARATOR_BY_URI.thenComparing(UriRouteInfo::getHttpMethodName))
            .collect(Collectors.groupingBy(KEY_MAPPER, LinkedHashMap::new, Collectors.toList()));
    }

    record Body(Map<String, List<UriRouteInfo<?, ?>>> appRoutes, Map<String, List<UriRouteInfo<?, ?>>> micronautRoutes) { }
}
