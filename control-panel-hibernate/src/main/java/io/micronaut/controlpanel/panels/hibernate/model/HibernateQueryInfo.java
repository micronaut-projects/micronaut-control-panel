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
 * Hibernate query statistics.
 *
 * @param query query string
 * @param executionCount execution count
 * @param executionRowCount execution row count
 * @param executionAvgTime average execution time
 * @param executionMaxTime maximum execution time
 * @param executionMinTime minimum execution time
 * @param executionTotalTime total execution time
 * @param cacheHitCount query cache hit count
 * @param cacheMissCount query cache miss count
 * @param cachePutCount query cache put count
 * @param planCacheHitCount query plan cache hit count
 * @param planCacheMissCount query plan cache miss count
 * @param slowTime slow query time
 */
@ReflectiveAccess
public record HibernateQueryInfo(
    String query,
    long executionCount,
    long executionRowCount,
    long executionAvgTime,
    long executionMaxTime,
    long executionMinTime,
    long executionTotalTime,
    long cacheHitCount,
    long cacheMissCount,
    long cachePutCount,
    long planCacheHitCount,
    long planCacheMissCount,
    long slowTime
) {
}
