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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointRabbitMqHealthResolverTest {

    @Test
    void resolvesAndRedactsRabbitMqHealthDetails() {
        HealthResult health = HealthResult.builder("rabbitmq", HealthStatus.DOWN)
            .details(Map.of(
                "host", "localhost",
                "password", "secret",
                "connections", List.of(Map.of(
                    "name", "primary",
                    "token", "abc123"
                ))
            ))
            .build();
        HealthEndpoint endpoint = Mockito.mock(HealthEndpoint.class);
        Mockito.when(endpoint.getHealth(null)).thenReturn(Mono.just(health));

        var resolved = new EndpointRabbitMqHealthResolver(endpoint).resolve();
        Map<String, String> details = detailsByName(resolved.details());

        assertTrue(resolved.available());
        assertEquals("DOWN", resolved.status());
        assertEquals("RabbitMQ health details are available.", resolved.message());
        assertEquals("localhost", details.get("host"));
        assertEquals(RabbitMqRedactor.REDACTED, details.get("password"));
        assertEquals("primary", details.get("connections[0].name"));
        assertEquals(RabbitMqRedactor.REDACTED, details.get("connections[0].token"));
    }

    @Test
    void findsNestedRabbitMqHealthAndReportsHiddenDetails() {
        HealthResult health = HealthResult.builder("composite", HealthStatus.UP)
            .details(Map.of(
                "systems", HealthResult.builder("systems", HealthStatus.UP)
                    .details(Map.of("rabbitmq", HealthResult.builder("rabbitmq", HealthStatus.UP).build()))
                    .build()
            ))
            .build();
        HealthEndpoint endpoint = Mockito.mock(HealthEndpoint.class);
        Mockito.when(endpoint.getHealth(null)).thenReturn(Mono.just(health));

        var resolved = new EndpointRabbitMqHealthResolver(endpoint).resolve();

        assertTrue(resolved.available());
        assertEquals("UP", resolved.status());
        assertEquals("RabbitMQ health details are hidden or not reported.", resolved.message());
        assertTrue(resolved.details().isEmpty());
    }

    @Test
    void reportsUnavailableWhenRabbitMqHealthIsAbsent() {
        HealthEndpoint endpoint = Mockito.mock(HealthEndpoint.class);
        Mockito.when(endpoint.getHealth(null)).thenReturn(Mono.empty());

        var resolved = new EndpointRabbitMqHealthResolver(endpoint).resolve();

        assertFalse(resolved.available());
        assertEquals("Unavailable", resolved.status());
        assertEquals("RabbitMQ health is not present in the health endpoint response.", resolved.message());
        assertTrue(resolved.details().isEmpty());
    }

    @Test
    void usesCurrentRequestPrincipalForHealthLookup() {
        Principal principal = () -> "sherlock";
        HealthResult health = HealthResult.builder("rabbitmq", HealthStatus.UP).build();
        HealthEndpoint endpoint = Mockito.mock(HealthEndpoint.class);
        Mockito.when(endpoint.getHealth(principal)).thenReturn(Mono.just(health));
        HttpRequest<?> request = HttpRequest.GET("/control-panel/rabbitmq");
        request.setUserPrincipal(principal);

        var resolved = ServerRequestContext.with(
            request,
            (java.util.function.Supplier<io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo>) () ->
                new EndpointRabbitMqHealthResolver(endpoint).resolve()
        );

        assertTrue(resolved.available());
        Mockito.verify(endpoint).getHealth(principal);
    }

    private static Map<String, String> detailsByName(List<io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo.HealthDetail> details) {
        return details.stream().collect(java.util.stream.Collectors.toMap(
            io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo.HealthDetail::name,
            io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqHealthInfo.HealthDetail::value
        ));
    }
}
