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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class RoutesControlPanel implements ControlPanel {

    private final Map<String, List<UriRoute>> appRoutes;
    private final Map<String, List<UriRoute>> micronautRoutes;

    public RoutesControlPanel(Router router) {
        Function<UriRoute, String> keyMapper = (UriRoute r) -> ((MethodBasedRoute) r).getTargetMethod().getDeclaringType().getName();
        Comparator<UriRoute> byUri = Comparator.comparing((UriRoute r) -> r.getUriMatchTemplate().toPathString());
        Predicate<UriRoute> isMicronautRoute = (UriRoute r) -> ((MethodBasedRoute) r).getTargetMethod().getDeclaringType().getPackage().getName().startsWith("io.micronaut");

        appRoutes = router.uriRoutes()
            .filter(isMicronautRoute.negate())
            .sorted(byUri.thenComparing(UriRoute::getHttpMethodName))
            .collect(Collectors.groupingBy(keyMapper, LinkedHashMap::new, Collectors.toList()));
        micronautRoutes = router.uriRoutes()
            .filter(isMicronautRoute)
            .sorted(byUri.thenComparing(UriRoute::getHttpMethodName))
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
