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
 * Connectivity state for a Neo4j driver.
 *
 * @param name stable status name
 * @param label display label
 * @param badgeClass badge CSS class
 * @param connected whether the driver is connected
 */
@ReflectiveAccess
public record Neo4jStatus(String name, String label, String badgeClass, boolean connected) {

    public static Neo4jStatus connectedStatus() {
        return new Neo4jStatus("connected", "Connected", "badge-success", true);
    }

    public static Neo4jStatus unavailable() {
        return new Neo4jStatus("unavailable", "Unavailable", "badge-warning", false);
    }

    public static Neo4jStatus noDrivers() {
        return new Neo4jStatus("no-drivers", "No driver beans", "badge-secondary", false);
    }
}
