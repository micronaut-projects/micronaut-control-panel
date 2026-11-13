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

import java.util.List;

/**
 * A NATS connection. Internal to the NATS panel views.
 *
 * @param name the connection name: {@code default} or another name under {@code nats}
 * @param addresses the configured server addresses, without user info
 * @param authentication how the connection authenticates: {@code user}, {@code token}, {@code credentials file} or
 * {@code none}, without the secret itself
 * @param tls whether TLS is configured
 * @param status the connection status, such as {@code CONNECTED}, or {@code null} if it has not been opened
 * @param connectedUrl the server the connection is connected to, without user info
 * @param server the server name and version, once connected
 * @param statistics the message counters, once connected
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record NatsConnectionInfo(
    String name,
    List<String> addresses,
    String authentication,
    boolean tls,
    @Nullable String status,
    @Nullable String connectedUrl,
    @Nullable String server,
    @Nullable Statistics statistics
) {

    /**
     * @return the CSS class of the status badge
     */
    public String badgeClass() {
        if (status == null || "CONNECTED".equals(status)) {
            return "badge-secondary";
        }
        return "badge-destructive";
    }

    /**
     * @return the label of the status badge
     */
    public String statusLabel() {
        return status == null ? "not opened yet" : status.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /**
     * Message counters since the connection was opened.
     *
     * @param inMessages the messages received
     * @param outMessages the messages sent
     * @param inBytes the bytes received
     * @param outBytes the bytes sent
     * @param reconnects the number of reconnections
     * @param dropped the messages dropped because a subscriber was too slow
     */
    @ReflectiveAccess
    public record Statistics(long inMessages, long outMessages, long inBytes, long outBytes, long reconnects, long dropped) {
    }
}
