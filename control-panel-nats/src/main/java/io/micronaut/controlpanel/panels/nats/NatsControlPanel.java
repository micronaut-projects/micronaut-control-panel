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
package io.micronaut.controlpanel.panels.nats;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.nats.connect.NatsConnectionFactoryConfig;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Read-only view of the NATS connections and {@code @NatsListener} subscriptions of the application.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = NatsConnectionFactoryConfig.class)
@Requires(property = NatsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class NatsControlPanel extends AbstractControlPanel<NatsControlPanel.Body> {

    public static final String NAME = "nats";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the messaging panels.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("messaging", "Messaging", "fas fa-envelope");

    private final NatsInspector inspector;

    /**
     * @param inspector the NATS inspector
     * @param configuration the panel configuration
     */
    public NatsControlPanel(NatsInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public Body getBody() {
        return new Body(inspector.connections(), inspector.subscriptions());
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
     * The NATS connections and subscriptions. Internal to the NATS panel views.
     *
     * @param connections the connections, sorted by name
     * @param subscriptions the {@code @Subject} methods, sorted by subject
     */
    @ReflectiveAccess
    public record Body(List<NatsConnectionInfo> connections, List<NatsSubscriptionInfo> subscriptions) {
    }
}
