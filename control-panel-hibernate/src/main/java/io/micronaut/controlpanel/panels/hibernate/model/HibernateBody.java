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

import java.util.List;

/**
 * Body of a Hibernate control panel.
 *
 * @param sessionFactory session factory metadata
 * @param statistics global Hibernate statistics
 * @param entities entity metadata and statistics
 * @param collections collection role statistics
 * @param queries query statistics
 * @param cacheRegions second-level cache region statistics
 * @param namedQueries named HQL, native, and callable queries
 * @param dataSources datasource and JDBC metadata
 */
@ReflectiveAccess
public record HibernateBody(
    HibernateSessionFactoryInfo sessionFactory,
    HibernateStatisticsInfo statistics,
    List<HibernateEntityInfo> entities,
    List<HibernateCollectionInfo> collections,
    List<HibernateQueryInfo> queries,
    List<HibernateCacheRegionInfo> cacheRegions,
    List<HibernateNamedQueryInfo> namedQueries,
    List<HibernateDataSourceInfo> dataSources
) {
    public HibernateBody(
        HibernateSessionFactoryInfo sessionFactory,
        HibernateStatisticsInfo statistics,
        List<HibernateEntityInfo> entities,
        List<HibernateCollectionInfo> collections,
        List<HibernateQueryInfo> queries,
        List<HibernateCacheRegionInfo> cacheRegions
    ) {
        this(sessionFactory, statistics, entities, collections, queries, cacheRegions, List.of(), List.of());
    }
}
