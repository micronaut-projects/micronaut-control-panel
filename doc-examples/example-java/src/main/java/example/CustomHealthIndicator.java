package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Map;

@Requires(property = "spec.name", notEquals = "ControlPanelShutdownE2ETest")
@Singleton
public class CustomHealthIndicator implements HealthIndicator {

    @Override
    public Publisher<HealthResult> getResult() {
        return Publishers.just(
            HealthResult.builder("customHealthIndicator", HealthStatus.UP)
                .details(Map.of(
                    "version", "1.0.0",
                    "component", "Custom Component",
                    "message", "Everything is working fine"
                ))
                .build()
        );
    }
}
