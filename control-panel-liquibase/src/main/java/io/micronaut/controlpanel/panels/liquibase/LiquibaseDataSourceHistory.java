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
package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Liquibase history for one configured datasource.
 *
 * @param name Liquibase datasource/configuration name
 * @param state machine-readable state
 * @param stateLabel display state
 * @param badgeClass Bootstrap badge class suffix
 * @param errorMessage read error message, when available
 * @param changeSets applied change sets
 * @param latestExecutionDate latest execution date
 * @param latestDeploymentId latest deployment id
 * @param latestTag latest tag
 */
@ReflectiveAccess
public record LiquibaseDataSourceHistory(
    String name,
    String state,
    String stateLabel,
    String badgeClass,
    @Nullable String errorMessage,
    List<LiquibaseChangeSet> changeSets,
    @Nullable String latestExecutionDate,
    @Nullable String latestDeploymentId,
    @Nullable String latestTag) {

    public static LiquibaseDataSourceHistory disabled(String name) {
        return new LiquibaseDataSourceHistory(name, "disabled", "Disabled", "secondary", null, List.of(), null, null, null);
    }

    public static LiquibaseDataSourceHistory error(String name, String errorMessage) {
        return new LiquibaseDataSourceHistory(name, "error", "Error", "danger", errorMessage, List.of(), null, null, null);
    }

    public static LiquibaseDataSourceHistory enabled(String name, List<LiquibaseChangeSet> changeSets) {
        if (changeSets.isEmpty()) {
            return new LiquibaseDataSourceHistory(name, "empty", "Empty history", "info", null, List.of(), null, null, null);
        }
        LiquibaseChangeSet latest = changeSets.stream()
            .max(Comparator
                .comparing(LiquibaseChangeSet::orderExecuted, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(LiquibaseChangeSet::dateExecuted, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(changeSets.get(changeSets.size() - 1));
        return new LiquibaseDataSourceHistory(
            name,
            "enabled",
            "OK",
            "success",
            null,
            changeSets,
            latest.dateExecuted(),
            latest.deploymentId(),
            latest.tag());
    }

    public int count() {
        return changeSets.size();
    }

    public boolean hasChangeSets() {
        return !changeSets.isEmpty();
    }

    public boolean isError() {
        return "error".equals(state);
    }

    public boolean isDisabled() {
        return "disabled".equals(state);
    }

    public boolean isEmptyHistory() {
        return "empty".equals(state);
    }
}
