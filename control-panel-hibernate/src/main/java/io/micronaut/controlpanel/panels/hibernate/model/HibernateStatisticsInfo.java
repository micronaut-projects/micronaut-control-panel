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
 * Aggregated Hibernate statistics.
 *
 * @param statisticsEnabled whether statistics collection is enabled
 * @param startedAt statistics start instant
 * @param sessionOpenCount opened session count
 * @param sessionCloseCount closed session count
 * @param transactionCount transaction count
 * @param successfulTransactionCount successful transaction count
 * @param connectCount connection count
 * @param flushCount flush count
 * @param prepareStatementCount prepared statement count
 * @param closeStatementCount closed statement count
 * @param entityLoadCount entity load count
 * @param entityFetchCount entity fetch count
 * @param entityInsertCount entity insert count
 * @param entityUpdateCount entity update count
 * @param entityDeleteCount entity delete count
 * @param collectionLoadCount collection load count
 * @param collectionFetchCount collection fetch count
 * @param collectionUpdateCount collection update count
 * @param collectionRemoveCount collection remove count
 * @param collectionRecreateCount collection recreate count
 * @param queryExecutionCount query execution count
 * @param queryExecutionMaxTime slowest query time
 * @param queryExecutionMaxTimeQueryString slowest query string
 * @param queryCacheHitCount query cache hit count
 * @param queryCacheMissCount query cache miss count
 * @param queryCachePutCount query cache put count
 * @param secondLevelCacheHitCount second-level cache hit count
 * @param secondLevelCacheMissCount second-level cache miss count
 * @param secondLevelCachePutCount second-level cache put count
 * @param naturalIdCacheHitCount natural-id cache hit count
 * @param naturalIdCacheMissCount natural-id cache miss count
 * @param naturalIdCachePutCount natural-id cache put count
 * @param queryPlanCacheHitCount query plan cache hit count
 * @param queryPlanCacheMissCount query plan cache miss count
 * @param optimisticFailureCount optimistic failure count
 */
@ReflectiveAccess
public record HibernateStatisticsInfo(
    boolean statisticsEnabled,
    String startedAt,
    long sessionOpenCount,
    long sessionCloseCount,
    long transactionCount,
    long successfulTransactionCount,
    long connectCount,
    long flushCount,
    long prepareStatementCount,
    long closeStatementCount,
    long entityLoadCount,
    long entityFetchCount,
    long entityInsertCount,
    long entityUpdateCount,
    long entityDeleteCount,
    long collectionLoadCount,
    long collectionFetchCount,
    long collectionUpdateCount,
    long collectionRemoveCount,
    long collectionRecreateCount,
    long queryExecutionCount,
    long queryExecutionMaxTime,
    String queryExecutionMaxTimeQueryString,
    long queryCacheHitCount,
    long queryCacheMissCount,
    long queryCachePutCount,
    long secondLevelCacheHitCount,
    long secondLevelCacheMissCount,
    long secondLevelCachePutCount,
    long naturalIdCacheHitCount,
    long naturalIdCacheMissCount,
    long naturalIdCachePutCount,
    long queryPlanCacheHitCount,
    long queryPlanCacheMissCount,
    long optimisticFailureCount
) {
}
