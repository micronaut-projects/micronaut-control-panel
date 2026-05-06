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
 * Hibernate entity metadata and runtime statistics.
 *
 * @param name JPA entity name
 * @param hibernateEntityName Hibernate entity name
 * @param javaType entity Java type
 * @param idType identifier type
 * @param attributeCount number of mapped attributes
 * @param attributes mapped attributes
 * @param statistics entity statistics
 */
@ReflectiveAccess
public record HibernateEntityInfo(
    String name,
    String hibernateEntityName,
    String javaType,
    String idType,
    int attributeCount,
    List<HibernateEntityAttributeInfo> attributes,
    HibernateEntityStatisticsInfo statistics
) {
}
