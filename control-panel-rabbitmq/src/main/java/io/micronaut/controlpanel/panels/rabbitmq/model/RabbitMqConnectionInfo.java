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
 * RabbitMQ connection and channel-pool diagnostics.
 *
 * @param name connection name
 * @param host host
 * @param port port
 * @param virtualHost virtual host
 * @param addresses configured address list
 * @param username redacted username value
 * @param requestedHeartbeat requested heartbeat in seconds
 * @param connectionTimeout connection timeout in milliseconds
 * @param handshakeTimeout handshake timeout in milliseconds
 * @param automaticRecovery automatic recovery flag
 * @param topologyRecovery topology recovery flag
 * @param networkRecoveryInterval network recovery interval in milliseconds
 * @param confirmTimeout publisher confirm timeout
 * @param rpcTimeout RPC timeout
 * @param maxIdleChannels channel pool maximum idle channels
 */
@ReflectiveAccess
public record RabbitMqConnectionInfo(
    String name,
    String host,
    int port,
    String virtualHost,
    List<String> addresses,
    String username,
    int requestedHeartbeat,
    int connectionTimeout,
    int handshakeTimeout,
    boolean automaticRecovery,
    boolean topologyRecovery,
    long networkRecoveryInterval,
    String confirmTimeout,
    String rpcTimeout,
    String maxIdleChannels
) {
}
