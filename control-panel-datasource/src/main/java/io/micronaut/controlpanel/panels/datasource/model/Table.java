/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.datasource.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;
import java.util.Set;

/**
 * Database table metadata.
 *
 * @param schema        The table schema
 * @param name          The table name
 * @param columns       The columns
 * @param uniqueColumns Columns that are part of unique indexes/constraints
 * @param foreignKeys   Foreign key relationships imported by this table
 */
@ReflectiveAccess
public record Table(String schema, String name, List<Column> columns, Set<String> uniqueColumns, List<ForeignKey> foreignKeys) {
}
