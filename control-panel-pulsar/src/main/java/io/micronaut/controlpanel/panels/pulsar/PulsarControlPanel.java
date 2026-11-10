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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Read-only view of the Pulsar client, consumers, producers and readers of the application.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(classes = PulsarConsumerRegistry.class)
@Requires(property = PulsarControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class PulsarControlPanel extends AbstractControlPanel<PulsarControlPanel.Body> {

    public static final String NAME = "pulsar";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * The category of the messaging panels.
     */
    static final ControlPanel.Category CATEGORY = new ControlPanel.Category("messaging", "Messaging", "fas fa-envelope");

    private final PulsarInspector inspector;

    /**
     * @param inspector the Pulsar inspector
     * @param configuration the panel configuration
     */
    public PulsarControlPanel(PulsarInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public Body getBody() {
        return new Body(inspector.client(), inspector.consumers(), inspector.producers(), inspector.readers());
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
     * The Pulsar client and its consumers, producers and readers. Internal to the Pulsar panel views.
     *
     * @param client the client configuration, or {@code null} if there is none
     * @param consumers the consumers, sorted by topic
     * @param producers the producers, sorted by topic
     * @param readers the readers, sorted by topic
     */
    @ReflectiveAccess
    public record Body(
        @Nullable PulsarClientInfo client,
        List<PulsarConsumerInfo> consumers,
        List<PulsarProducerInfo> producers,
        List<PulsarReaderInfo> readers
    ) {

        /**
         * @return the number of consumers that are not connected
         */
        public long disconnectedConsumers() {
            return consumers.stream().filter(consumer -> !consumer.connected()).count();
        }
    }
}
