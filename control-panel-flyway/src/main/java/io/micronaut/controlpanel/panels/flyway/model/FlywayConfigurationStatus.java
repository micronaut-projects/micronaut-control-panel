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

import java.util.List;

/**
 * Flyway datasource/configuration status.
 *
 * @param name bean/configuration name
 * @param available whether metadata could be read
 * @param statusLabel display status
 * @param statusBadgeClass badge CSS class
 * @param currentVersion current migration version
 * @param latestApplied latest applied migration
 * @param totalMigrations total migrations reported by Flyway
 * @param message unavailable or empty-state message
 * @param migrations migration details
 * @param stateSummaries state group counts
 */
@ReflectiveAccess
public record FlywayConfigurationStatus(
    String name,
    boolean available,
    String statusLabel,
    String statusBadgeClass,
    String currentVersion,
    String latestApplied,
    int totalMigrations,
    String message,
    List<FlywayMigration> migrations,
    List<FlywayStateSummary> stateSummaries
) {
}
