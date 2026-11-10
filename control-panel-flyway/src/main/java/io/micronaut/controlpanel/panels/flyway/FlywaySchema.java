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

import java.util.List;

/**
 * The migration history of one Flyway configuration. Internal to the Flyway panel views.
 *
 * @param name the configuration name, as in {@code flyway.datasources.<name>}
 * @param status the status of the configuration
 * @param currentVersion the version of the latest applied migration, if any
 * @param migrations the migrations, in Flyway order
 * @param applied the number of applied migrations
 * @param pending the number of pending migrations
 * @param failed the number of failed migrations
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record FlywaySchema(
    String name,
    Status status,
    @Nullable String currentVersion,
    List<FlywayMigration> migrations,
    long applied,
    long pending,
    long failed
) {

    /**
     * @param name the configuration name
     * @param status a status for which no migration information is available
     * @return a schema without migrations
     */
    static FlywaySchema withoutMigrations(String name, Status status) {
        return new FlywaySchema(name, status, null, List.of(), 0, 0, 0);
    }

    /**
     * @return the label of the summary badge
     */
    public String summary() {
        if (status != Status.READ) {
            return status.label;
        }
        if (failed > 0) {
            return failed + " failed";
        }
        if (pending > 0) {
            return pending + " pending";
        }
        return "up to date";
    }

    /**
     * @return the CSS class of the summary badge
     */
    public String badgeClass() {
        if (status == Status.UNREADABLE || failed > 0) {
            return "badge-destructive";
        }
        if (status == Status.READ && pending > 0) {
            return "badge-primary";
        }
        return "badge-secondary";
    }

    /**
     * @return a description of the status, or {@code null} when the history was read
     */
    public @Nullable String statusMessage() {
        return status.message;
    }

    /**
     * Whether the migration history could be read.
     */
    public enum Status {
        /**
         * The history was read.
         */
        READ(null, null),
        /**
         * The configuration is disabled.
         */
        DISABLED("disabled", "Flyway is disabled for this datasource in the configuration."),
        /**
         * Flyway has not run yet for the configuration, for example because it runs asynchronously.
         */
        NOT_RUN("not run yet", "Flyway has not run for this configuration yet. Asynchronous migrations appear once they start."),
        /**
         * The history could not be read.
         */
        UNREADABLE("unavailable", "The migration history could not be read. The application log has the error at DEBUG level.");

        private final @Nullable String label;
        private final @Nullable String message;

        Status(@Nullable String label, @Nullable String message) {
            this.label = label;
            this.message = message;
        }
    }
}
