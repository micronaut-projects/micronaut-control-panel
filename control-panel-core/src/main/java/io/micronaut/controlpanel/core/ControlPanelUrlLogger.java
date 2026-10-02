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
package io.micronaut.controlpanel.core;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.controlpanel.util.ControlPanelUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Logs the Control Panel URL when the application is ready.
 */
@Singleton
@Requires(property = ControlPanelModuleConfiguration.PREFIX + ".log-url", notEquals = StringUtils.FALSE)
public class ControlPanelUrlLogger {

    private static final Logger LOG = LoggerFactory.getLogger(ControlPanelUrlLogger.class);

    private final String applicationPath;
    private final String controlPanelPath;
    private final Optional<String> applicationName;

    public ControlPanelUrlLogger(HttpServerConfiguration serverConfiguration,
                                 ControlPanelModuleConfiguration controlPanelConfiguration,
                                 ApplicationConfiguration applicationConfiguration) {
        this.applicationPath = Optional.ofNullable(serverConfiguration.getContextPath()).orElse("");
        this.controlPanelPath = controlPanelConfiguration.getPath();
        this.applicationName = applicationConfiguration.getName();
    }

    /**
     * Logs the Control Panel URL when the server has started.
     *
     * @param event the server startup event that triggered this method call
     */
    @EventListener
    public void logUrl(ServerStartupEvent event) {
        var baseUrl = event.getSource().getURI().toString();
        var controlPanelUrl = baseUrl + ControlPanelUtils.computeControlPanelPath(applicationPath, controlPanelPath);
        var prefix = applicationName.map("[%s]"::formatted).orElse("");
        LOG.info("{} Control Panel available at {}", prefix, controlPanelUrl);
    }

}
