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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.SortedSet;

/**
 * Control panel that displays Micrometer metric names and details.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@Singleton
@Requires(beans = MetricsEndpoint.class)
@Requires(property = MetricsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class MetricsControlPanel extends AbstractControlPanel<MetricsControlPanel.MetricsBody> {

    public static final String NAME = "metrics";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final MetricsEndpoint endpoint;

    public MetricsControlPanel(MetricsEndpoint endpoint, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.endpoint = endpoint;
    }

    @Override
    public MetricsBody getBody() {
        return new MetricsBody(endpoint.listNames().getNames());
    }

    @Override
    public String getBadge() {
        return String.valueOf(endpoint.listNames().getNames().size());
    }

    /**
     * Body model for the metrics panel summary.
     *
     * @param names sorted metric names
     */
    @ReflectiveAccess
    public record MetricsBody(SortedSet<String> names) {
    }
}
