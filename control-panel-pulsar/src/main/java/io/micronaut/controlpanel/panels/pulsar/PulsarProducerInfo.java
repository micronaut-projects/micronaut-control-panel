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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * A Pulsar producer. Internal to the Pulsar panel views.
 *
 * @param id the producer id in the producer registry
 * @param topic the topic
 * @param producerName the producer name
 * @param connected whether the producer is connected to the broker
 * @param sent the messages sent
 * @param failed the messages that could not be sent
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record PulsarProducerInfo(String id, String topic, String producerName, boolean connected, long sent, long failed) {

    /**
     * @return the CSS class of the status badge
     */
    public String badgeClass() {
        return connected ? "badge-secondary" : "badge-destructive";
    }
}
