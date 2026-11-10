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

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

/**
 * A {@code @Queue} consumer method of a {@code @RabbitListener}. Internal to the RabbitMQ panel views.
 *
 * @param queue the queue name
 * @param listener the listener class
 * @param method the method name
 * @param connection the connection name
 * @param executor the executor name, or {@code null} for the consumer executor of the connection
 * @param consumers the number of consumers, as configured
 * @param prefetch the prefetch count, or 0 for no limit
 * @param autoAcknowledgment whether messages are acknowledged as soon as they are delivered
 * @param reQueue whether rejected messages are requeued
 * @param exclusive whether the consumers are exclusive
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record RabbitMqConsumerInfo(
    String queue,
    String listener,
    String method,
    String connection,
    @Nullable String executor,
    String consumers,
    int prefetch,
    boolean autoAcknowledgment,
    boolean reQueue,
    boolean exclusive
) {

    /**
     * @return the number of consumers, as text
     */
    public String consumersLabel() {
        return consumers + ("1".equals(consumers) ? " consumer" : " consumers");
    }
}
