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
 * Foreign key relationship metadata.
 *
 * @param name      The foreign key name
 * @param fkColumn  The foreign key column name on the current table
 * @param pkSchema  The primary key table schema
 * @param pkTable   The primary key table name
 * @param pkColumn  The primary key column name
 */
@ReflectiveAccess
public record ForeignKey(String name, String fkColumn, String pkSchema, String pkTable, String pkColumn) {
}
