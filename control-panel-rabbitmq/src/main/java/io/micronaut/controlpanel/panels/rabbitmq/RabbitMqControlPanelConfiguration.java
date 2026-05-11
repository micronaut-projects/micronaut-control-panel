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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Optional;

/**
 * RabbitMQ panel configuration.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@ConfigurationProperties(RabbitMqControlPanelConfiguration.PREFIX)
public class RabbitMqControlPanelConfiguration {

    /**
     * RabbitMQ panel configuration prefix.
     */
    public static final String PREFIX = ControlPanelConfiguration.PREFIX + "." + RabbitMqControlPanel.NAME;

    private @Nullable String managementUrl;

    /**
     * Constructor.
     */
    public RabbitMqControlPanelConfiguration() {
    }

    /**
     * The optional RabbitMQ Management UI URL.
     *
     * @return optional RabbitMQ Management UI URL
     */
    public Optional<String> getManagementUrl() {
        return Optional.ofNullable(managementUrl).filter(RabbitMqControlPanelConfiguration::isHttpUrl);
    }

    /**
     * Sets the optional outbound RabbitMQ Management UI URL.
     *
     * @param managementUrl management UI URL
     */
    public void setManagementUrl(@Nullable String managementUrl) {
        this.managementUrl = managementUrl;
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
