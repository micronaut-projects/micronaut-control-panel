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
 * Hibernate session factory metadata.
 *
 * @param beanName Micronaut bean name
 * @param sessionFactoryName Hibernate session factory name
 * @param closed whether the session factory is closed
 * @param statisticsEnabled whether statistics collection is enabled
 * @param secondLevelCacheEnabled whether the second-level cache is enabled
 * @param queryCacheEnabled whether the query cache is enabled
 * @param defaultCatalog default catalog
 * @param defaultSchema default schema
 * @param cacheRegionPrefix cache region prefix
 * @param properties selected safe Hibernate properties
 */
@ReflectiveAccess
public record HibernateSessionFactoryInfo(
    String beanName,
    String sessionFactoryName,
    boolean closed,
    boolean statisticsEnabled,
    boolean secondLevelCacheEnabled,
    boolean queryCacheEnabled,
    String defaultCatalog,
    String defaultSchema,
    String cacheRegionPrefix,
    Map<String, String> properties
) {
}
