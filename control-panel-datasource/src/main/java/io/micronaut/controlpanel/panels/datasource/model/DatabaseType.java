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
 * Represents the database type, which can be inferred from either the Micronaut Data's configured
 * dialect, or the Micronaut Test Resources' configured db type.
 */
@ReflectiveAccess
public enum DatabaseType {
    MYSQL, POSTGRES, SQL_SERVER, ORACLE, MARIADB, GENERIC;

    public static DatabaseType of(String dialect, String dbType) {
        return switch (dialect) {
            case "MYSQL" -> MYSQL;
            case "POSTGRES" -> POSTGRES;
            case "SQL_SERVER" -> SQL_SERVER;
            case "ORACLE" -> ORACLE;
            default -> ofDbType(dbType);
        };
    }

    private static DatabaseType ofDbType(String dbType) {
        return switch (dbType) {
            case "mariadb" -> MARIADB;
            case "mysql" -> MYSQL;
            case "oracle-xe", "oracle" -> ORACLE;
            case "postgres" -> POSTGRES;
            case "mssql" -> SQL_SERVER;
            default -> GENERIC;
        };
    }
}
