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
package io.micronaut.controlpanel.core.controlpanels.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Control panel that displays information about the application health.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Requires(beans = HealthEndpoint.class)
public class HealthControlPanel implements ControlPanel<HealthResult> {

    public static final int ORDER = 0;

    private final HealthEndpoint endpoint;

    public HealthControlPanel(HealthEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String getTitle() {
        return "Application health";
    }

    @Override
    public HealthResult getBody() {
        return Mono.from(endpoint.getHealth(null)).block();
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }

    @Override
    public String getName() {
        return HealthEndpoint.NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getIcon() {
        return "fa-laptop-medical";
    }
}
