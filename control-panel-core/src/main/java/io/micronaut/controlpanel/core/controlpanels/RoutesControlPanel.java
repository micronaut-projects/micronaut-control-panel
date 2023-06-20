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
package io.micronaut.controlpanel.core.controlpanels;

import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
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
public class RoutesControlPanel implements ControlPanel<RoutesControlPanel.Body> {

    private static final int ORDER = 20;
    private final Map<String, List<UriRouteInfo<?, ?>>> appRoutes;
    private final Map<String, List<UriRouteInfo<?, ?>>> micronautRoutes;

    public RoutesControlPanel(Router router) {
        Function<UriRouteInfo<?, ?>, String> keyMapper = r -> r.getTargetMethod().getDeclaringType().getName();
        Comparator<UriRouteInfo<?, ?>> byUri = Comparator.comparing(r -> r.getUriMatchTemplate().toPathString());
        Predicate<UriRouteInfo<?, ?>> isMicronautRoute = r -> r.getTargetMethod().getDeclaringType().getPackage().getName().startsWith("io.micronaut");

        appRoutes = router.uriRoutes()
            .filter(isMicronautRoute.negate())
            .sorted(byUri.thenComparing(UriRouteInfo::getHttpMethodName))
            .collect(Collectors.groupingBy(keyMapper, LinkedHashMap::new, Collectors.toList()));
        micronautRoutes = router.uriRoutes()
            .filter(isMicronautRoute)
            .sorted(byUri.thenComparing(UriRouteInfo::getHttpMethodName))
            .collect(Collectors.groupingBy(keyMapper, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public String getTitle() {
        return "HTTP Routes";
    }

    @Override
    public Body getBody() {
        return new Body(appRoutes, micronautRoutes);
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }

    @Override
    public String getName() {
        return "routes";
    }

    @Override
    public String getBadge() {
        int totalAppRoutes = appRoutes.values().stream().mapToInt(List::size).sum();
        int totalMicronautRoutes = micronautRoutes.values().stream().mapToInt(List::size).sum();
        return String.valueOf(totalAppRoutes + totalMicronautRoutes);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getIcon() {
        return "fa-route";
    }

    record Body(Map<String, List<UriRouteInfo<?, ?>>> appRoutes, Map<String, List<UriRouteInfo<?, ?>>> micronautRoutes) { }
}
