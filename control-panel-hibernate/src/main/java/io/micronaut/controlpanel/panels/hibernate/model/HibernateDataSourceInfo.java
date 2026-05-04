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
package io.micronaut.controlpanel.panels.hibernate.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.Map;

/**
 * Hibernate datasource and JDBC metadata.
 *
 * @param name datasource name
 * @param jdbcUrl JDBC URL
 * @param jdbcDriver JDBC driver
 * @param dialect Hibernate dialect
 * @param dialectVersion dialect database version
 * @param catalog connection catalog
 * @param schema connection schema
 * @param autoCommitMode autocommit mode
 * @param isolationLevel transaction isolation level
 * @param poolMinSize connection pool minimum size
 * @param poolMaxSize connection pool maximum size
 * @param jdbcFetchSize JDBC fetch size
 * @param supportsSchemas whether the database supports schemas
 * @param supportsCatalogs whether the database supports catalogs
 * @param supportsNamedParameters whether JDBC named parameters are supported
 * @param supportsScrollableResults whether scrollable results are supported
 * @param supportsBatchUpdates whether JDBC batch updates are supported
 * @param supportsGetGeneratedKeys whether generated keys are supported
 * @param properties selected datasource properties
 */
@ReflectiveAccess
public record HibernateDataSourceInfo(
    String name,
    String jdbcUrl,
    String jdbcDriver,
    String dialect,
    String dialectVersion,
    String catalog,
    String schema,
    String autoCommitMode,
    String isolationLevel,
    String poolMinSize,
    String poolMaxSize,
    String jdbcFetchSize,
    boolean supportsSchemas,
    boolean supportsCatalogs,
    boolean supportsNamedParameters,
    boolean supportsScrollableResults,
    boolean supportsBatchUpdates,
    boolean supportsGetGeneratedKeys,
    Map<String, String> properties
) {
}
