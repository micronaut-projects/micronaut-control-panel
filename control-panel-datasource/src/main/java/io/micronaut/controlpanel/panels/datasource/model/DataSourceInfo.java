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
 * DataSource information.
 *
 * @param name     The DataSource name
 * @param jdbcUrl  The JDBC URL
 * @param username The database username
 * @param password The database password
 * @param type     The database type/dialect
 * @param jdbcInfo Additional JDBC metadata
 */
@ReflectiveAccess
public record DataSourceInfo(String name, String jdbcUrl, String username, String password, DatabaseType type, JdbcInfo jdbcInfo) {

    public DataSourceInfo(String name, String jdbcUrl, String username, String password, DatabaseType type) {
        this(name, jdbcUrl, username, password, type, JdbcInfo.EMPTY);
    }

    public DataSourceInfo {
        if (jdbcInfo == null) {
            jdbcInfo = JdbcInfo.EMPTY;
        }
    }
}
