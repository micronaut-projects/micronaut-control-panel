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
package io.micronaut.controlpanel.panels.nats;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

/**
 * A {@code @Subject} method of a {@code @NatsListener}. Internal to the NATS panel views.
 *
 * @param subject the subject, which can contain wildcards
 * @param queue the queue group, if any
 * @param listener the listener class
 * @param method the method name
 * @param connection the connection name
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record NatsSubscriptionInfo(String subject, @Nullable String queue, String listener, String method, String connection) {
}
