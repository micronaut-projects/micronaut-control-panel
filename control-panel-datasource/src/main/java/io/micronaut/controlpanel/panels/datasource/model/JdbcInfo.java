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
 * JDBC metadata exposed for a datasource.
 *
 * @param database      The database product name
 * @param version       The database product version
 * @param driverName    The JDBC driver name
 * @param driverVersion The JDBC driver version
 * @param catalog       The current catalog
 * @param schema        The current schema
 */
@ReflectiveAccess
public record JdbcInfo(
    String database,
    String version,
    String driverName,
    String driverVersion,
    String catalog,
    String schema
) {

    public static final JdbcInfo EMPTY = new JdbcInfo("", "", "", "", "", "");

    public JdbcInfo {
        database = valueOrEmpty(database);
        version = valueOrEmpty(version);
        driverName = valueOrEmpty(driverName);
        driverVersion = valueOrEmpty(driverVersion);
        catalog = valueOrEmpty(catalog);
        schema = valueOrEmpty(schema);
    }

    /**
     * @return true if at least one metadata field is available
     */
    public boolean present() {
        return !database.isBlank()
            || !version.isBlank()
            || !driverName.isBlank()
            || !driverVersion.isBlank()
            || !catalog.isBlank()
            || !schema.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
