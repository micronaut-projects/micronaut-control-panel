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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Locale;
import java.util.Map;

/**
 * Control-panel-owned helper controller for logger mutations.
 */
@Controller(ControlPanelSecurityPaths.LOGGERS)
@ExecuteOn(TaskExecutors.BLOCKING)
@Requires(beans = { LoggersEndpoint.class, ManagedLoggingSystem.class })
@Requires(property = LoggersControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
@Internal
public final class LoggersController {

    private final LoggersEndpoint loggersEndpoint;

    /**
     * Constructor.
     *
     * @param loggersEndpoint the management loggers endpoint
     */
    public LoggersController(LoggersEndpoint loggersEndpoint) {
        this.loggersEndpoint = loggersEndpoint;
    }

    /**
     * Reconfigures the level for the requested logger.
     *
     * @param logger the logger name
     * @param request the requested level
     * @return HTTP 204 No Content if successful, HTTP 400 Bad Request for invalid input
     */
    @Post(value = "/{logger}", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<?> configure(String logger, @Body ConfigureLoggerRequest request) {
        if (isBlank(logger)) {
            return badRequest("Logger name must not be blank");
        }
        if (request == null || isBlank(request.configuredLevel())) {
            return badRequest("Configured level must not be blank");
        }

        LogLevel configuredLevel;
        try {
            configuredLevel = LogLevel.valueOf(request.configuredLevel().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid configured level");
        }

        loggersEndpoint.setLogLevel(logger, configuredLevel);
        return HttpResponse.noContent();
    }

    private static HttpResponse<Map<String, String>> badRequest(String message) {
        return HttpResponse.badRequest(Map.of("message", message));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Request DTO for logger level changes.
     *
     * @param configuredLevel the requested logger level
     */
    @Introspected
    public record ConfigureLoggerRequest(String configuredLevel) {
    }
}
