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
 * Hibernate entity statistics.
 *
 * @param loadCount entity load count
 * @param fetchCount entity fetch count
 * @param insertCount entity insert count
 * @param updateCount entity update count
 * @param deleteCount entity delete count
 * @param optimisticFailureCount optimistic failure count
 * @param cacheRegionName cache region name
 * @param cacheHitCount cache hit count
 * @param cacheMissCount cache miss count
 * @param cachePutCount cache put count
 */
@ReflectiveAccess
public record HibernateEntityStatisticsInfo(
    long loadCount,
    long fetchCount,
    long insertCount,
    long updateCount,
    long deleteCount,
    long optimisticFailureCount,
    String cacheRegionName,
    long cacheHitCount,
    long cacheMissCount,
    long cachePutCount
) {
}
