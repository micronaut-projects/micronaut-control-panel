/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.rabbitmq;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo.HealthDetail;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves RabbitMQ health through the Micronaut health endpoint.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@Singleton
@Requires(classes = HealthEndpoint.class)
@Requires(beans = HealthEndpoint.class)
final class EndpointRabbitMqHealthResolver implements RabbitMqHealthResolver {

    private static final String RABBITMQ = "rabbitmq";

    private final HealthEndpoint healthEndpoint;

    EndpointRabbitMqHealthResolver(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @Override
    public RabbitMqHealthInfo resolve() {
        Principal principal = ServerRequestContext.currentRequest()
            .flatMap(request -> request.getUserPrincipal(Principal.class))
            .orElse(null);
        HealthResult result = Mono.from(healthEndpoint.getHealth(principal)).block();
        HealthResult rabbit = findRabbitMq(result);
        if (rabbit == null) {
            return new RabbitMqHealthInfo(false, "Unavailable", "RabbitMQ health is not present in the health endpoint response.", List.of());
        }
        Object details = rabbit.getDetails();
        String message = details == null ? "RabbitMQ health details are hidden or not reported." : "RabbitMQ health details are available.";
        return new RabbitMqHealthInfo(true, rabbit.getStatus().getName(), message, flatten(details));
    }

    private static @Nullable HealthResult findRabbitMq(@Nullable HealthResult result) {
        if (result == null) {
            return null;
        }
        if (RABBITMQ.equals(result.getName())) {
            return result;
        }
        Object details = result.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object direct = map.get(RABBITMQ);
            if (direct instanceof HealthResult healthResult) {
                return healthResult;
            }
            for (Object value : map.values()) {
                if (value instanceof HealthResult healthResult) {
                    HealthResult found = findRabbitMq(healthResult);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private static List<HealthDetail> flatten(@Nullable Object value) {
        List<HealthDetail> details = new ArrayList<>();
        flatten(details, "", value);
        return details;
    }

    private static void flatten(List<HealthDetail> details, String prefix, @Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = prefix.isBlank() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                flatten(details, key, entry.getValue());
            }
        } else if (value instanceof Iterable<?> iterable) {
            int i = 0;
            for (Object item : iterable) {
                flatten(details, prefix + "[" + i + "]", item);
                i++;
            }
        } else if (value != null && !prefix.isBlank()) {
            details.add(new HealthDetail(prefix, RabbitMqRedactor.redactKeyValue(prefix, value)));
        }
    }
}
