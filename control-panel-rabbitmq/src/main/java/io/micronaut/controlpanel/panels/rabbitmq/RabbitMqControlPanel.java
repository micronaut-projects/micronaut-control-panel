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
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.rabbitmq.connect.RabbitConnectionFactoryConfig;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Read-only view of the RabbitMQ connections and {@code @RabbitListener} consumers of the application.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = RabbitConnectionFactoryConfig.class)
@Requires(property = RabbitMqControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class RabbitMqControlPanel extends AbstractControlPanel<RabbitMqControlPanel.Body> {

    public static final String NAME = "rabbitmq";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the messaging panels.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("messaging", "Messaging", "fas fa-envelope");

    private final RabbitMqInspector inspector;

    /**
     * @param inspector the RabbitMQ inspector
     * @param configuration the panel configuration
     */
    public RabbitMqControlPanel(RabbitMqInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public Body getBody() {
        return new Body(inspector.connections(), inspector.consumers());
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    /**
     * The RabbitMQ connections and consumers. Internal to the RabbitMQ panel views.
     *
     * @param connections the connections, sorted by name
     * @param consumers the {@code @Queue} consumer methods, sorted by queue
     */
    @ReflectiveAccess
    public record Body(List<RabbitMqConnectionInfo> connections, List<RabbitMqConsumerInfo> consumers) {

        /**
         * @return whether every connection that has been opened is still open
         */
        public boolean allOpen() {
            return connections.stream().allMatch(connection -> connection.state() != RabbitMqConnectionInfo.State.CLOSED);
        }
    }
}
