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
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.management.endpoint.env.EnvironmentEndpoint;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Control panel that displays information about the environment properties.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Requires(beans = EnvironmentEndpoint.class)
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
    public Map<String, Object> getBody() {
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
