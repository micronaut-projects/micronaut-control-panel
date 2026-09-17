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
package io.micronaut.controlpanel.panels.flyway.model;

import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * Flyway migration metadata rendered by the control panel.
 *
 * @param version migration version, or repeatable when no version is reported
 * @param description migration description
 * @param type migration type
 * @param script migration script name
 * @param state raw Flyway state name
 * @param stateLabel display state label
 * @param stateGroup normalized state group
 * @param badgeClass badge CSS class
 * @param installedRank installed rank
 * @param installedOn installed timestamp
 * @param executionTime execution time in milliseconds
 * @param checksum checksum
 * @param installedBy installed-by user
 */
@ReflectiveAccess
public record FlywayMigration(
    String version,
    String description,
    String type,
    String script,
    String state,
    String stateLabel,
    String stateGroup,
    String badgeClass,
    String installedRank,
    String installedOn,
    String executionTime,
    String checksum,
    String installedBy
) {
}
