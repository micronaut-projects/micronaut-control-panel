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
 * RabbitMQ metrics diagnostics.
 *
 * @param available whether Micrometer is available
 * @param message metrics message
 * @param prefix RabbitMQ meter prefix
 * @param meters RabbitMQ meters
 */
@ReflectiveAccess
public record RabbitMqMetricsInfo(boolean available, String message, String prefix, List<MeterInfo> meters) {

    /**
     * Meter details.
     *
     * @param name meter name
     * @param type meter type
     * @param baseUnit base unit
     * @param description description
     * @param tags tags
     * @param measurements measurements
     */
    @ReflectiveAccess
    public record MeterInfo(String name,
                            String type,
                            @Nullable String baseUnit,
                            @Nullable String description,
                            List<TagInfo> tags,
                            List<MeasurementInfo> measurements) {
    }

    /**
     * Meter tag.
     *
     * @param key tag key
     * @param value tag value
     */
    @ReflectiveAccess
    public record TagInfo(String key, String value) {
    }

    /**
     * Meter measurement.
     *
     * @param statistic statistic
     * @param value value
     */
    @ReflectiveAccess
    public record MeasurementInfo(String statistic, double value) {
    }
}
