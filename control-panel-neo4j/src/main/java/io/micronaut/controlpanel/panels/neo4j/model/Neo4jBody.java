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

import java.util.List;

/**
 * Body of the Neo4j control panel.
 *
 * @param connection safe connection summary
 * @param status connectivity status
 * @param server server metadata
 * @param labels bounded labels
 * @param relationshipTypes bounded relationship types
 * @param propertyKeys bounded property keys
 * @param diagnostics non-fatal diagnostics
 * @param noDriverBeans whether no Driver beans were found
 */
@ReflectiveAccess
public record Neo4jBody(
    Neo4jConnectionInfo connection,
    Neo4jStatus status,
    Neo4jServerInfo server,
    List<String> labels,
    List<String> relationshipTypes,
    List<String> propertyKeys,
    List<Neo4jDiagnostic> diagnostics,
    boolean noDriverBeans
) {
    private static final Neo4jConnectionInfo EMPTY_CONNECTION = new Neo4jConnectionInfo("", "No Neo4j Driver beans detected", "", "", "");

    public static Neo4jBody noDrivers() {
        return new Neo4jBody(
            EMPTY_CONNECTION,
            Neo4jStatus.noDrivers(),
            Neo4jServerInfo.EMPTY,
            List.of(),
            List.of(),
            List.of(),
            List.of(new Neo4jDiagnostic("Driver beans", "No Neo4j Driver beans are available in this application context.", false)),
            true
        );
    }

    public boolean connected() {
        return status.connected();
    }

    public int labelCount() {
        return labels.size();
    }

    public int relationshipTypeCount() {
        return relationshipTypes.size();
    }

    public int propertyKeyCount() {
        return propertyKeys.size();
    }

    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }

    public boolean hasServerInfo() {
        return server.hasInfo();
    }
}
