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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Named;
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
@Requires(property = HealthControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class HealthControlPanel extends AbstractControlPanel<HealthResult> {

    public static final String NAME = HealthEndpoint.NAME;
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final HealthEndpoint endpoint;

    public HealthControlPanel(HealthEndpoint endpoint, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.endpoint = endpoint;
    }

    @Override
    public HealthResult getBody() {
        return Mono.from(endpoint.getHealth(null)).block();
    }

}
