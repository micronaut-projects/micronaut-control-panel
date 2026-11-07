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
 * The change log history of one Liquibase configuration. Internal to the Liquibase panel views.
 *
 * @param name the configuration name, as in {@code liquibase.datasources.<name>}
 * @param changeLog the configured change log file
 * @param status whether the history could be read
 * @param changeSets the change sets that ran, in execution order
 * @param failed the number of change sets that failed
 * @param lastTag the most recent tag, if any
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record LiquibaseHistory(
    String name,
    @Nullable String changeLog,
    Status status,
    List<LiquibaseChangeSet> changeSets,
    long failed,
    @Nullable String lastTag
) {

    /**
     * @param name the configuration name
     * @param changeLog the configured change log file
     * @param status a status for which no history is available
     * @return a history without change sets
     */
    static LiquibaseHistory withoutChangeSets(String name, @Nullable String changeLog, Status status) {
        return new LiquibaseHistory(name, changeLog, status, List.of(), 0, null);
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
        return changeSets.size() + " ran";
    }

    /**
     * @return the CSS class of the summary badge
     */
    public String badgeClass() {
        return status == Status.UNREADABLE || failed > 0 ? "badge-destructive" : "badge-secondary";
    }

    /**
     * @return a description of the status, or {@code null} when the history was read
     */
    public @Nullable String statusMessage() {
        return status.message;
    }

    /**
     * Whether the history could be read.
     */
    public enum Status {
        /**
         * The history was read.
         */
        READ(null, null),
        /**
         * The configuration is disabled.
         */
        DISABLED("disabled", "Liquibase is disabled for this datasource in the configuration."),
        /**
         * The history could not be read.
         */
        UNREADABLE("unavailable", "The change log history could not be read. The application log has the error at DEBUG level.");

        private final @Nullable String label;
        private final @Nullable String message;

        Status(@Nullable String label, @Nullable String message) {
            this.label = label;
            this.message = message;
        }
    }
}
