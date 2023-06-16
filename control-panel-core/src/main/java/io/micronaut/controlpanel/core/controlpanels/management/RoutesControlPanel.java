package io.micronaut.controlpanel.core.controlpanels.management;

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
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class RoutesControlPanel implements ControlPanel {

    private final Map<String, List<UriRouteInfo<?,?>>> appRoutes;
    private final Map<String, List<UriRouteInfo<?,?>>> micronautRoutes;

    public RoutesControlPanel(Router router) {
        Function<UriRouteInfo<?,?>, String> keyMapper = r -> r.getTargetMethod().getDeclaringType().getName();
        Comparator<UriRouteInfo<?,?>> byUri = Comparator.comparing(r -> r.getUriMatchTemplate().toPathString());
        Predicate<UriRouteInfo<?,?>> isMicronautRoute = r -> r.getTargetMethod().getDeclaringType().getPackage().getName().startsWith("io.micronaut");

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
    public Map<String, Object> getModel() {
        return Map.of(
            "appRoutes", appRoutes,
            "micronautRoutes", micronautRoutes
        );
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
        return HealthControlPanel.ORDER + 20;
    }

    @Override
    public String getIcon() {
        return "fa-route";
    }
}
