package io.micronaut.controlpanel.core.management;

import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.management.endpoint.routes.RouteDataCollector;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class RoutesControlPanel implements ControlPanel {

    private final Map<String, List<UriRoute>> routesByDeclaringType;

    public RoutesControlPanel(Router router) {
        Function<UriRoute, String> keyMapper = (UriRoute r) -> ((MethodBasedRoute) r).getTargetMethod().getDeclaringType().getName();
        this.routesByDeclaringType = router.uriRoutes()
            .sorted(Comparator
                .comparing((UriRoute r) -> r.getUriMatchTemplate().toPathString())
                .thenComparing(UriRoute::getHttpMethodName))
            .collect(Collectors.groupingBy(keyMapper, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public String getTitle() {
        return "HTTP Routes";
    }

    @Override
    public Map<String, Object> getModel() {
        return Map.of(
            "routesByDeclaringType", routesByDeclaringType
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
        return String.valueOf(routesByDeclaringType.values().stream().mapToInt(List::size).sum());
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
