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
package io.micronaut.controlpanel.panels.neo4j.model;

import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * Server metadata returned by Neo4j result summaries.
 *
 * @param agent server agent
 * @param address server address
 * @param protocolVersion Bolt protocol version
 */
@ReflectiveAccess
public record Neo4jServerInfo(String agent, String address, String protocolVersion) {
    public static final Neo4jServerInfo EMPTY = new Neo4jServerInfo("", "", "");

    public boolean hasInfo() {
        return !isBlank(agent) || !isBlank(address) || !isBlank(protocolVersion);
    }

    public boolean hasAgent() {
        return !isBlank(agent);
    }

    public boolean hasAddress() {
        return !isBlank(address);
    }

    public boolean hasProtocolVersion() {
        return !isBlank(protocolVersion);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
