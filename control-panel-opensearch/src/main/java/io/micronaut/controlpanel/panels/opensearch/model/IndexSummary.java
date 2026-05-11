/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.opensearch.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Bounded index metadata.
 */
@ReflectiveAccess
public record IndexSummary(String name,
                           String health,
                           String status,
                           String primaryShards,
                           String replicaShards,
                           String documentCount,
                           String storeSize,
                           List<String> aliases,
                           List<MappingField> mappingFields,
                           boolean mappingTruncated,
                           boolean hasAliases,
                           boolean hasMappingFields) {

    public IndexSummary(String name,
                        String health,
                        String status,
                        String primaryShards,
                        String replicaShards,
                        String documentCount,
                        String storeSize,
                        List<String> aliases,
                        List<MappingField> mappingFields,
                        boolean mappingTruncated) {
        this(name, health, status, primaryShards, replicaShards, documentCount, storeSize, aliases, mappingFields, mappingTruncated, false, false);
    }

    public IndexSummary {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        mappingFields = mappingFields == null ? List.of() : List.copyOf(mappingFields);
        hasAliases = !aliases.isEmpty();
        hasMappingFields = !mappingFields.isEmpty();
    }
}
