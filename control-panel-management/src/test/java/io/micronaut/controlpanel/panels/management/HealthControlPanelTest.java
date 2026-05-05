/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicator;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class HealthControlPanelTest {

    @Test
    void itIsConfiguredCorrectly() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(ServiceReadyHealthIndicator.ENABLED, false))) {
            HealthControlPanel panel = ctx.getBean(HealthControlPanel.class);
            assertEquals("Application Health", panel.getTitle());
            assertEquals("fa-laptop-medical", panel.getIcon());
            assertEquals(0, panel.getOrder());
            assertEquals(HealthStatus.UP, panel.getBody().getStatus());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(HealthControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(HealthControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(HealthControlPanel.class));
        }
    }

    @Test
    void itUsesTheCurrentRequestPrincipalForAuthenticatedHealthDetails() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "HealthControlPanelTest",
            ServiceReadyHealthIndicator.ENABLED, false,
            "endpoints.health.details-visible", "AUTHENTICATED"
        ))) {
            HealthControlPanel panel = ctx.getBean(HealthControlPanel.class);

            Object anonymousDetails = ServerRequestContext.with(HttpRequest.GET("/control-panel/health"), (Supplier<Object>) () -> panel.getBody().getDetails());
            assertNull(anonymousDetails);

            MutableHttpRequest<?> authenticatedRequest = HttpRequest.GET("/control-panel/health");
            authenticatedRequest.setUserPrincipal((Principal) () -> "user");
            Object authenticatedDetails = ServerRequestContext.with(authenticatedRequest, (Supplier<Object>) () -> panel.getBody().getDetails());
            @SuppressWarnings("unchecked")
            Map<String, ?> aggregatedDetails = (Map<String, ?>) authenticatedDetails;
            HealthResult detailed = (HealthResult) aggregatedDetails.get("detailed");
            assertEquals(Map.of("secret", "visible-to-authenticated-users"), detailed.getDetails());
        }
    }

    @Requires(property = "spec.name", value = "HealthControlPanelTest")
    @Singleton
    static class DetailedHealthIndicator implements HealthIndicator {

        @Override
        public org.reactivestreams.Publisher<io.micronaut.management.health.indicator.HealthResult> getResult() {
            return Mono.just(io.micronaut.management.health.indicator.HealthResult.builder("detailed", HealthStatus.UP)
                .details(Map.of("secret", "visible-to-authenticated-users"))
                .build());
        }
    }
}
