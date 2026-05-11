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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Metrics resolver used when Micrometer is not available.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@Singleton
@Requires(missingBeans = RabbitMqMetricsResolver.class)
final class UnavailableRabbitMqMetricsResolver implements RabbitMqMetricsResolver {

    @Override
    public RabbitMqMetricsInfo resolve() {
        return new RabbitMqMetricsInfo(false, "Micrometer is not available in this application.", "rabbitmq", List.of());
    }
}
