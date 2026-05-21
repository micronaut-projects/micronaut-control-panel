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
package io.micronaut.controlpanel.panels.crac;

import io.micronaut.crac.OrderedResource;

/**
 * Conservative classifier for Micronaut CRaC resource types.
 */
final class CracResourceClassifier {

    private static final String MICRONAUT_CRAC_PACKAGE = "io.micronaut.crac.";

    private CracResourceClassifier() {
    }

    static String category(OrderedResource resource) {
        String className = resource.getClass().getName();
        if (className.contains(".resources.redis.")) {
            return "redis";
        }
        if (className.contains(".resources.datasources.") || className.endsWith(".DataSourceResource")) {
            return "datasource";
        }
        if (className.endsWith(".RefreshEventResource")) {
            return "refresh";
        }
        if (className.endsWith(".NettyEmbeddedServerResource")) {
            return "netty";
        }
        if (className.startsWith(MICRONAUT_CRAC_PACKAGE)) {
            return "unknown";
        }
        return "custom";
    }

    static String readinessNote(String category) {
        return switch (category) {
            case "refresh" -> "Refresh scope lifecycle is managed before checkpoint and after restore.";
            case "netty" -> "Embedded server lifecycle is managed by Micronaut CRaC.";
            case "datasource" -> "Datasource resource detected; verify pool suspension support in datasource configuration.";
            case "redis" -> "Redis CRaC resource detected; verify Redis clients and caches are recreated after restore.";
            case "custom" -> "Custom ordered resource; verify it closes files, sockets, and threads before checkpoint.";
            default -> "Micronaut CRaC resource detected, but the specific readiness category is unknown.";
        };
    }

    static String simpleName(Object value) {
        String simpleName = value.getClass().getSimpleName();
        return simpleName.isBlank() ? value.getClass().getName() : simpleName;
    }
}
