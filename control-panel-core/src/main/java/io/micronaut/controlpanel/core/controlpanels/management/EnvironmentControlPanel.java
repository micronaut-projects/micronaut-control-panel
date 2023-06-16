package io.micronaut.controlpanel.core.controlpanels.management;

import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.management.endpoint.env.EnvironmentEndpoint;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
public class EnvironmentControlPanel implements ControlPanel {

    private final EnvironmentEndpoint endpoint;

    private final long totalProperties;

    public EnvironmentControlPanel(EnvironmentEndpoint endpoint, Environment environment) {
        this.endpoint = endpoint;
        this.totalProperties = environment.getPropertySources().stream()
            .map(ps -> StreamSupport.stream(ps.spliterator(), false).count())
            .mapToLong(Long::longValue)
            .sum();
    }

    @Override
    public String getTitle() {
        return "Environment properties";
    }

    @Override
    public Map<String, Object> getModel() {
        Map<String, Object> environmentInfo = endpoint.getEnvironmentInfo();
        return Map.of(
            "environmentInfo", environmentInfo
        );
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }

    @Override
    public String getName() {
        return EnvironmentEndpoint.NAME;
    }

    @Override
    public String getBadge() {
        return String.valueOf(totalProperties);
    }

    @Override
    public int getOrder() {
        return HealthControlPanel.ORDER + 10;
    }

    @Override
    public String getIcon() {
        return "fa-sliders";
    }
}
