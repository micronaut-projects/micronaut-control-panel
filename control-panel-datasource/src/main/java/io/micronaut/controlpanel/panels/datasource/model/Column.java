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

/**
 * Database column metadata.
 *
 * @param name         The column name
 * @param type         The generic column type
 * @param size         The column size
 * @param nullable     Whether the column is nullable
 * @param binary       Whether the column is binary
 * @param isPrimaryKey Whether the column is a primary key
 * @param isForeignKey Whether the column is a foreign key
 */
@ReflectiveAccess
public record Column(String name, ColumnType type, int size, String nullable, boolean binary,
                     boolean isPrimaryKey, boolean isForeignKey) {
}
