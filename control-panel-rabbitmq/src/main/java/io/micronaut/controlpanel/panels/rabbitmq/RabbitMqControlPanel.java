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
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqBody;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Control panel for RabbitMQ listener, connection, health, and metrics diagnostics.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@Singleton
@Requires(property = RabbitMqControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class RabbitMqControlPanel extends AbstractControlPanel<RabbitMqBody> {

    /**
     * RabbitMQ panel name.
     */
    public static final String NAME = "rabbitmq";

    /**
     * Configuration property used to enable or disable the RabbitMQ panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * Messaging category used by the RabbitMQ panel.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("messaging", "Messaging", "fas fa-message");

    private final RabbitMqDiagnosticsService diagnosticsService;

    /**
     * Constructor.
     *
     * @param diagnosticsService RabbitMQ diagnostics service
     * @param configuration control panel configuration
     */
    public RabbitMqControlPanel(RabbitMqDiagnosticsService diagnosticsService,
                                @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public RabbitMqBody getBody() {
        return diagnosticsService.getBody();
    }

    @Override
    public String getBadge() {
        return String.valueOf(getBody().listenerCount());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }
}
