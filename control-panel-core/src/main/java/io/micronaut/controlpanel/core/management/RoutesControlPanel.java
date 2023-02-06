package io.micronaut.controlpanel.core.management;

import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.management.endpoint.routes.RouteDataCollector;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class RoutesControlPanel implements ControlPanel {

    private final List<UriRoute> routes;

    public RoutesControlPanel(Router router) {
        this.routes = router.uriRoutes()
            .sorted(Comparator
                .comparing((UriRoute r) -> r.getUriMatchTemplate().toPathString())
                .thenComparing(UriRoute::getHttpMethodName))
            .toList();

    }

    @Override
    public String getTitle() {
        return "HTTP Routes";
    }

    @Override
    public Map<String, Object> getModel() {
        return Map.of(
            "routes", routes
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
        return String.valueOf(routes.size());
    }

    @Override
    public int getOrder() {
        return HealthControlPanel.ORDER + 20;
    }
}
