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

import java.util.List;

/**
 * Applied Liquibase change-set metadata used by the templates.
 *
 * @param orderExecuted order in the Liquibase history table
 * @param id change-set id
 * @param author change-set author
 * @param changeLog changelog path
 * @param storedChangeLog stored changelog path
 * @param description change-set description
 * @param comments change-set comments
 * @param execType execution type
 * @param dateExecuted execution date as an ISO-8601 string
 * @param deploymentId Liquibase deployment id
 * @param contexts contexts attached to the change-set
 * @param labels labels attached to the change-set
 * @param checksum stored checksum
 * @param tag Liquibase tag
 */
@ReflectiveAccess
public record LiquibaseChangeSet(
    @Nullable Integer orderExecuted,
    String id,
    String author,
    String changeLog,
    @Nullable String storedChangeLog,
    @Nullable String description,
    @Nullable String comments,
    @Nullable String execType,
    @Nullable String dateExecuted,
    @Nullable String deploymentId,
    List<String> contexts,
    List<String> labels,
    @Nullable String checksum,
    @Nullable String tag) {
}
