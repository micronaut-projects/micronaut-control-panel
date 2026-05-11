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
package io.micronaut.controlpanel.panels.rabbitmq.model;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * RabbitMQ panel body.
 *
 * @param listeners listener diagnostics
 * @param connections connection diagnostics
 * @param health health diagnostics
 * @param metrics metrics diagnostics
 * @param managementUrl optional RabbitMQ Management UI URL
 * @param listenerCount listener count
 * @param connectionCount connection count
 * @param metricsPresent whether RabbitMQ meters are present
 */
@ReflectiveAccess
public record RabbitMqBody(
    List<RabbitMqListenerInfo> listeners,
    List<RabbitMqConnectionInfo> connections,
    RabbitMqHealthInfo health,
    RabbitMqMetricsInfo metrics,
    @Nullable String managementUrl,
    int listenerCount,
    int connectionCount,
    boolean metricsPresent
) {
}
