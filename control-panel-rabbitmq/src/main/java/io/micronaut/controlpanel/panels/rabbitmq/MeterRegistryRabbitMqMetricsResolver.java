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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo.MeasurementInfo;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo.MeterInfo;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqMetricsInfo.TagInfo;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Resolves RabbitMQ Micrometer meters from the active registry.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@Singleton
@Requires(classes = MeterRegistry.class)
@Requires(beans = MeterRegistry.class)
final class MeterRegistryRabbitMqMetricsResolver implements RabbitMqMetricsResolver {

    private static final String DEFAULT_PREFIX = "rabbitmq";

    private final MeterRegistry registry;
    private final String prefix;

    MeterRegistryRabbitMqMetricsResolver(MeterRegistry registry,
                                         @Nullable @Property(name = "micronaut.metrics.binders.rabbitmq.prefix") String prefix) {
        this.registry = registry;
        this.prefix = prefix == null || prefix.isBlank() ? DEFAULT_PREFIX : prefix;
    }

    @Override
    public RabbitMqMetricsInfo resolve() {
        List<MeterInfo> meters = registry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith(prefix))
            .map(this::meter)
            .sorted(Comparator.comparing(MeterInfo::name))
            .toList();
        String message = meters.isEmpty()
            ? "No RabbitMQ Micrometer meters were found. Enable Micrometer and the RabbitMQ metrics binder to populate this section."
            : "RabbitMQ Micrometer meters are available.";
        return new RabbitMqMetricsInfo(true, message, prefix, meters);
    }

    private MeterInfo meter(Meter meter) {
        Meter.Id id = meter.getId();
        return new MeterInfo(
            id.getName(),
            id.getType().name(),
            id.getBaseUnit(),
            id.getDescription(),
            id.getTags().stream()
                .map(tag -> new TagInfo(tag.getKey(), RabbitMqRedactor.redactKeyValue(tag.getKey(), tag.getValue())))
                .toList(),
            StreamSupport.stream(meter.measure().spliterator(), false)
                .map(measurement -> new MeasurementInfo(measurement.getStatistic().name(), measurement.getValue()))
                .toList()
        );
    }
}
