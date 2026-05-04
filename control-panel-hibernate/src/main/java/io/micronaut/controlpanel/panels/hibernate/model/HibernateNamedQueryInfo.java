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

/**
 * Hibernate named query metadata.
 *
 * @param name registration name
 * @param type query type
 * @param query query text or callable name
 * @param cacheable whether Hibernate should cache the query results
 * @param cacheRegion query cache region
 * @param readOnly whether results are read-only
 * @param timeout timeout in seconds
 * @param fetchSize JDBC fetch size
 * @param comment query comment
 */
@ReflectiveAccess
public record HibernateNamedQueryInfo(
    String name,
    String type,
    String query,
    String cacheable,
    String cacheRegion,
    String readOnly,
    String timeout,
    String fetchSize,
    String comment
) {
}
