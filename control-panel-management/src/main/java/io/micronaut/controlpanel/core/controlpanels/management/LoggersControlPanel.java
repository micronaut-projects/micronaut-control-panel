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
package io.micronaut.controlpanel.core.controlpanels.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
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
public class LoggersControlPanel implements ControlPanel {

    private final LoggersEndpoint endpoint;
    private final ManagedLoggingSystem loggingSystem;
    private final List<LogLevel> levels;

    public LoggersControlPanel(LoggersEndpoint endpoint, ManagedLoggingSystem loggingSystem) {
        this.endpoint = endpoint;
        this.loggingSystem = loggingSystem;
        this.levels = Arrays.asList(io.micronaut.logging.LogLevel.values());
    }

    @Override
    public String getTitle() {
        return "Loggers";
    }

    @Override
    public Map<String, Object> getModel() {
        LinkedHashMap<String, Map<String, Object>> loggers = getLoggers()
            .sorted(Comparator.comparing(LoggerConfiguration::getName))
            .collect(Collectors.toMap(LoggerConfiguration::getName, LoggerConfiguration::getData, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        return Map.of(
            "levels", levels,
            "loggers", loggers
        );
    }

    @Override
    public Category getCategory() {
        return Category.MAIN;
    }

    @Override
    public String getName() {
        return LoggersEndpoint.NAME;
    }

    @Override
    public String getBadge() {
        return String.valueOf(getLoggers().count());
    }

    @Override
    public int getOrder() {
        return HealthControlPanel.ORDER + 40;
    }

    @Override
    public String getIcon() {
        return "fa-file-lines";
    }

    private Stream<LoggerConfiguration> getLoggers() {
        return loggingSystem.getLoggers()
            .stream()
            .filter(loggerConfiguration -> loggerConfiguration.configuredLevel() != LogLevel.NOT_SPECIFIED);
    }
}
