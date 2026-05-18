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
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Control panel that displays information about the environment properties.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Requires(beans = EnvironmentEndpoint.class)
@Requires(property = EnvironmentControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class EnvironmentControlPanel extends AbstractControlPanel<Map<String, Object>> {

    public static final String NAME = EnvironmentEndpoint.NAME;
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    private final EnvironmentEndpoint endpoint;

    public EnvironmentControlPanel(EnvironmentEndpoint endpoint, Environment environment, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.endpoint = endpoint;
    }

    @Override
    public Map<String, Object> getBody() {
        var body = new HashMap<>(endpoint.getEnvironmentInfo());
        if (body.get("activeEnvironments") instanceof Collection<?> activeEnvironments) {
            body.put("activeEnvironments", List.copyOf(activeEnvironments));
        }
        return body;
    }

    @Override
    public String getBadge() {
        return EMPTY_STRING;
    }

    @Override
    public String getDetailLinkName() {
        return "Browse properties";
    }

}
