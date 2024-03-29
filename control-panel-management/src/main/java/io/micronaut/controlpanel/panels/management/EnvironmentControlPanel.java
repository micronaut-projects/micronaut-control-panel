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
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.env.EnvironmentEndpoint;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
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
@Refreshable
@Requires(beans = EnvironmentEndpoint.class)
@Requires(property = EnvironmentControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class EnvironmentControlPanel extends AbstractControlPanel<Map<String, Object>> {

    public static final String NAME = EnvironmentEndpoint.NAME;
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    private final EnvironmentEndpoint endpoint;

    private final long totalProperties;

    public EnvironmentControlPanel(EnvironmentEndpoint endpoint, Environment environment, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.endpoint = endpoint;
        this.totalProperties = environment.getPropertySources().stream()
            .map(ps -> StreamSupport.stream(ps.spliterator(), false).count())
            .mapToLong(Long::longValue)
            .sum();
    }

    @Override
    public Map<String, Object> getBody() {
        return endpoint.getEnvironmentInfo();
    }

    @Override
    public String getBadge() {
        return String.valueOf(totalProperties);
    }

}
