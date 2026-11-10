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
 * A change set that ran. Internal to the Liquibase panel views.
 *
 * @param orderExecuted the execution order
 * @param id the change set id
 * @param author the change set author
 * @param changeLog the change log file that declares the change set
 * @param description the description Liquibase generated from the changes
 * @param comments the comments
 * @param execType how the change set ran, such as {@code EXECUTED} or {@code MARK_RAN}
 * @param badgeClass the CSS class of the execution type badge
 * @param dateExecuted when the change set ran, in UTC
 * @param tag the tag, if any
 * @param contexts the contexts
 * @param labels the labels
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record LiquibaseChangeSet(
    @Nullable Integer orderExecuted,
    String id,
    String author,
    String changeLog,
    @Nullable String description,
    @Nullable String comments,
    String execType,
    String badgeClass,
    @Nullable String dateExecuted,
    @Nullable String tag,
    List<String> contexts,
    List<String> labels
) {
}
