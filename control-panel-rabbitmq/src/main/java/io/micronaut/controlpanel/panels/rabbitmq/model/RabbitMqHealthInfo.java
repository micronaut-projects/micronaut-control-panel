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

import java.util.List;

/**
 * RabbitMQ health diagnostics.
 *
 * @param available whether health is available
 * @param status health status
 * @param message health message
 * @param details health details
 */
@ReflectiveAccess
public record RabbitMqHealthInfo(boolean available, String status, String message, List<HealthDetail> details) {

    /**
     * Health detail entry.
     *
     * @param name detail name
     * @param value detail value
     */
    @ReflectiveAccess
    public record HealthDetail(String name, String value) {
    }
}
