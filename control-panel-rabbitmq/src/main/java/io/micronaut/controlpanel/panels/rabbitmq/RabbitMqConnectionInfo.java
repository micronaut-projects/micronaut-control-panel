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

import java.util.List;

/**
 * A RabbitMQ connection. Internal to the RabbitMQ panel views.
 *
 * @param name the connection name: {@code default}, or the name under {@code rabbitmq.servers}
 * @param addresses the broker addresses, as {@code host:port}
 * @param virtualHost the virtual host
 * @param username the user name
 * @param ssl whether TLS is enabled
 * @param consumerExecutor the executor that runs the consumers
 * @param channelPoolMaxIdle the maximum number of idle channels in the pool, if set
 * @param state whether the connection is open
 * @param broker the broker product and version, once connected
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record RabbitMqConnectionInfo(
    String name,
    List<String> addresses,
    String virtualHost,
    String username,
    boolean ssl,
    @Nullable String consumerExecutor,
    @Nullable Integer channelPoolMaxIdle,
    State state,
    @Nullable String broker
) {

    /**
     * @return the CSS class of the state badge
     */
    public String badgeClass() {
        return state == State.CLOSED ? "badge-destructive" : "badge-secondary";
    }

    /**
     * @return the label of the state badge
     */
    public String stateLabel() {
        return switch (state) {
            case OPEN -> "open";
            case CLOSED -> "closed";
            case NOT_OPENED -> "not opened yet";
        };
    }

    /**
     * The state of the connection, as the client knows it.
     */
    public enum State {
        /**
         * The connection is open.
         */
        OPEN,
        /**
         * The connection was opened and is now closed, for example because the broker went away.
         */
        CLOSED,
        /**
         * Nothing has used the connection yet, so it has not been opened.
         */
        NOT_OPENED
    }
}
