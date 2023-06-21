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
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Control panel that displays information about the application loggers.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
@Requires(beans = { LoggersEndpoint.class, ManagedLoggingSystem.class })
@Requires(property = LoggersControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class LoggersControlPanel extends AbstractControlPanel<LoggersControlPanel.Body> {

    public static final String NAME = LoggersEndpoint.NAME;
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final ManagedLoggingSystem loggingSystem;
    private final List<LogLevel> levels;

    public LoggersControlPanel(ManagedLoggingSystem loggingSystem, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.loggingSystem = loggingSystem;
        this.levels = Arrays.asList(LogLevel.values());
    }

    @Override
    public Body getBody() {
        var loggers = getLoggers()
            .sorted(Comparator.comparing(LoggerConfiguration::getName))
            .collect(Collectors.toMap(LoggerConfiguration::getName, LoggerConfiguration::getData, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        return new Body(levels, loggers);
    }

    @Override
    public String getBadge() {
        return String.valueOf(getLoggers().count());
    }

    private Stream<LoggerConfiguration> getLoggers() {
        return loggingSystem.getLoggers()
            .stream()
            .filter(loggerConfiguration -> loggerConfiguration.configuredLevel() != LogLevel.NOT_SPECIFIED);
    }

    record Body(List<LogLevel> levels, Map<String, Map<String, Object>> loggers) { }
}
