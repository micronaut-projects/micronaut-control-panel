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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelEnabledCondition;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.env.EnvironmentEndpointFilter;
import io.micronaut.management.endpoint.env.EnvironmentFilterSpecification;
import jakarta.inject.Singleton;

/**
 * Built-in environment endpoint filter that can be enabled via configuration to display
 * actual configuration values with legacy masking (masking only sensitive keys like passwords).
 *
 * This filter can be enabled by setting the configuration property:
 * {@code micronaut.control-panel.env.show-values=true}
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.8.1
 */
@Singleton
@Requires(property = AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Requires(classes = EnvironmentEndpointFilter.class)
@Requires(condition = ControlPanelEnabledCondition.class)
public class AllPlainEnvironmentEndpointFilter implements EnvironmentEndpointFilter {

    public static final String ENABLED_PROPERTY = ControlPanelModuleConfiguration.PREFIX + ".env.show-values";

    @Override
    public void specifyFiltering(@NonNull EnvironmentFilterSpecification specification) {
        specification.legacyMasking();
    }
}
