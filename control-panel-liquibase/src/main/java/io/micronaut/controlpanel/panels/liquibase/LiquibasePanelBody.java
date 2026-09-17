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

import java.util.List;

/**
 * Aggregate Liquibase panel body.
 *
 * @param dataSources datasource histories
 * @param showChecksums whether checksum columns should be shown
 * @param showDeploymentIds whether deployment ID columns should be shown
 */
@ReflectiveAccess
public record LiquibasePanelBody(
    List<LiquibaseDataSourceHistory> dataSources,
    boolean showChecksums,
    boolean showDeploymentIds) {

    public boolean hasConfigurations() {
        return !dataSources.isEmpty();
    }

    public int totalChangeSets() {
        return dataSources.stream().mapToInt(LiquibaseDataSourceHistory::count).sum();
    }

    public long enabledCount() {
        return dataSources.stream().filter(history -> "enabled".equals(history.state()) || "empty".equals(history.state())).count();
    }

    public long disabledCount() {
        return dataSources.stream().filter(LiquibaseDataSourceHistory::isDisabled).count();
    }

    public long errorCount() {
        return dataSources.stream().filter(LiquibaseDataSourceHistory::isError).count();
    }
}
