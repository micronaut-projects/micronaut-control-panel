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
package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

/**
 * A Flyway migration. Internal to the Flyway panel views.
 *
 * @param version the version, or {@code null} for repeatable migrations
 * @param description the description
 * @param type the migration type, such as {@code SQL} or {@code JDBC}
 * @param script the script name
 * @param state the Flyway state, such as {@code Success} or {@code Pending}
 * @param badgeClass the CSS class of the state badge
 * @param installedOn when the migration was applied, in UTC, if applied
 * @param installedBy the database user that applied the migration, if applied
 * @param executionTime the execution time in milliseconds, if applied
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record FlywayMigration(
    @Nullable String version,
    String description,
    String type,
    String script,
    String state,
    String badgeClass,
    @Nullable String installedOn,
    @Nullable String installedBy,
    @Nullable Integer executionTime
) {
}
