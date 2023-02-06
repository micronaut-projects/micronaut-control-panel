package io.micronaut.controlpanel.core.management;

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
 * TODO: add javadoc.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Singleton
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

    private Stream<LoggerConfiguration> getLoggers() {
        return loggingSystem.getLoggers()
            .stream()
            .filter(loggerConfiguration -> loggerConfiguration.configuredLevel() != LogLevel.NOT_SPECIFIED);
    }
}
