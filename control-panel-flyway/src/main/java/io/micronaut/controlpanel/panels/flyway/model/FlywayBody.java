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
 * Flyway panel body.
 *
 * @param configurations Flyway configurations
 * @param stateSummaries aggregate state group counts
 * @param totalConfigurations total configuration count
 * @param totalMigrations total migration count
 * @param availableConfigurations configuration count with readable metadata
 * @param unavailableConfigurations configuration count with unreadable metadata
 * @param showChecksums whether checksums are rendered
 * @param showInstalledBy whether installed-by users are rendered
 */
@ReflectiveAccess
public record FlywayBody(
    List<FlywayConfigurationStatus> configurations,
    List<FlywayStateSummary> stateSummaries,
    int totalConfigurations,
    int totalMigrations,
    int availableConfigurations,
    int unavailableConfigurations,
    boolean showChecksums,
    boolean showInstalledBy
) {
    /**
     * Whether there are Flyway configurations to display.
     *
     * @return true when there are Flyway configurations to display
     */
    public boolean hasConfigurations() {
        return totalConfigurations > 0;
    }
}
