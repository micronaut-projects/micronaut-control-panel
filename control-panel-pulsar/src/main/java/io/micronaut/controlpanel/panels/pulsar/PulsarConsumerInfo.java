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
 * A Pulsar consumer. Internal to the Pulsar panel views.
 *
 * @param id the consumer id in the consumer registry
 * @param topic the topic, or the topics the consumer subscribes to
 * @param subscription the subscription name
 * @param consumerName the consumer name
 * @param connected whether the consumer is connected to the broker
 * @param paused whether the consumer is paused
 * @param received the messages received
 * @param acknowledged the acknowledgements sent
 * @param failed the messages that could not be received
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record PulsarConsumerInfo(
    String id,
    String topic,
    String subscription,
    String consumerName,
    boolean connected,
    boolean paused,
    long received,
    long acknowledged,
    long failed
) {

    /**
     * @return the label of the status badge
     */
    public String statusLabel() {
        if (!connected) {
            return "disconnected";
        }
        return paused ? "paused" : "connected";
    }

    /**
     * @return the CSS class of the status badge
     */
    public String badgeClass() {
        return connected ? "badge-secondary" : "badge-destructive";
    }
}
