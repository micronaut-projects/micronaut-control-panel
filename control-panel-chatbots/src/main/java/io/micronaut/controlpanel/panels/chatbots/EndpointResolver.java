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
package io.micronaut.controlpanel.panels.chatbots;

import io.micronaut.context.env.Environment;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;

final class EndpointResolver {

    private EndpointResolver() {
    }

    static ChatbotsControlPanel.EndpointRow endpoint(Environment environment,
                                                     Router router,
                                                     String channel,
                                                     String controllerClassName,
                                                     String enabledProperty,
                                                     String pathProperty,
                                                     String defaultPath) {
        boolean modulePresent = ClassUtils.isPresent(controllerClassName, EndpointResolver.class.getClassLoader());
        boolean configuredEnabled = environment.getProperty(enabledProperty, Boolean.class).orElse(true);
        String path = environment.getProperty(pathProperty, String.class).orElse(defaultPath);
        boolean routeRegistered = modulePresent && router.uriRoutes()
            .map(UriRouteInfo::getTargetMethod)
            .anyMatch(method -> method.getDeclaringType().getName().equals(controllerClassName));
        return new ChatbotsControlPanel.EndpointRow(
            channel,
            modulePresent,
            configuredEnabled,
            routeRegistered,
            path,
            state(modulePresent, configuredEnabled, routeRegistered),
            environment.containsProperty(pathProperty) ? "configuration" : "default"
        );
    }

    private static String state(boolean modulePresent, boolean configuredEnabled, boolean routeRegistered) {
        if (!modulePresent) {
            return "HTTP module absent";
        }
        if (!configuredEnabled) {
            return "configured disabled";
        }
        if (routeRegistered) {
            return "registered";
        }
        return "configured enabled but route not registered";
    }
}
