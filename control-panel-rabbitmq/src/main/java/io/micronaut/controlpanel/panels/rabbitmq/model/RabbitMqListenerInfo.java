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

/**
 * RabbitMQ listener method diagnostics.
 *
 * @param beanType fully-qualified bean type
 * @param beanSimpleName simple bean type name
 * @param beanName bean definition name
 * @param declaringType declaring type
 * @param method method name
 * @param signature method signature
 * @param queue queue name
 * @param connection connection name
 * @param executor executor bean name
 * @param numberOfConsumers number of consumers
 * @param prefetch prefetch setting
 * @param exclusive exclusive consumer flag
 * @param reQueue nack requeue flag
 * @param autoAcknowledgment automatic acknowledgement flag
 * @param acknowledgementArgument whether the method accepts an acknowledgement argument
 */
@ReflectiveAccess
public record RabbitMqListenerInfo(
    String beanType,
    String beanSimpleName,
    String beanName,
    String declaringType,
    String method,
    String signature,
    String queue,
    String connection,
    String executor,
    String numberOfConsumers,
    int prefetch,
    boolean exclusive,
    boolean reQueue,
    boolean autoAcknowledgment,
    boolean acknowledgementArgument
) {
}
