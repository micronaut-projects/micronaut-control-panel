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
package io.micronaut.controlpanel.panels.mongodb;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class MongoDbSchemaAnalyzer {

    private final MongoDbControlPanelConfiguration configuration;
    private final MongoDbSanitizer sanitizer;

    MongoDbSchemaAnalyzer(MongoDbControlPanelConfiguration configuration, MongoDbSanitizer sanitizer) {
        this.configuration = configuration;
        this.sanitizer = sanitizer;
    }

    MongoDbModels.SchemaSummary disabled() {
        return MongoDbModels.SchemaSummary.disabled();
    }

    MongoDbModels.SchemaSummary summarize(List<Document> documents) {
        if (!configuration.getSchema().isEnabled()) {
            return disabled();
        }
        Map<String, FieldAccumulator> fields = new LinkedHashMap<>();
        int maxFields = Math.max(1, configuration.getSchema().getMaxFields());
        for (Document document : documents) {
            visit("", document, 0, fields, maxFields, new HashSet<>());
        }
        boolean truncated = fields.size() >= maxFields;
        int samples = documents.size();
        List<MongoDbModels.SchemaField> schemaFields = fields.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getValue().toField(entry.getKey(), samples))
            .toList();
        return new MongoDbModels.SchemaSummary(true, samples, truncated, schemaFields, List.of());
    }

    private void visit(String prefix,
                       Document document,
                       int depth,
                       Map<String, FieldAccumulator> fields,
                       int maxFields,
                       Set<String> documentPaths) {
        if (depth >= configuration.getSchema().getMaxDepth() || fields.size() >= maxFields) {
            return;
        }
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if (fields.size() >= maxFields) {
                return;
            }
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = configuration.getSchema().isRedactValues()
                ? sanitizer.redactValue(entry.getKey(), entry.getValue())
                : entry.getValue();
            fields.computeIfAbsent(path, ignored -> new FieldAccumulator())
                .add(AbstractMongoDbDiagnosticService.bsonType(value), documentPaths.add(path));
            if (value instanceof Document nested) {
                visit(path, nested, depth + 1, fields, maxFields, documentPaths);
            } else if (value instanceof List<?> list) {
                visitList(path, list, depth + 1, fields, maxFields, documentPaths);
            }
        }
    }

    private void visitList(String path,
                           List<?> list,
                           int depth,
                           Map<String, FieldAccumulator> fields,
                           int maxFields,
                           Set<String> documentPaths) {
        if (depth >= configuration.getSchema().getMaxDepth() || fields.size() >= maxFields) {
            return;
        }
        int index = 0;
        for (Object item : list) {
            if (index >= configuration.getSchema().getSampleSize() || fields.size() >= maxFields) {
                return;
            }
            if (item instanceof Document document) {
                visit(path + "[]", document, depth, fields, maxFields, documentPaths);
            } else {
                String itemPath = path + "[]";
                fields.computeIfAbsent(itemPath, ignored -> new FieldAccumulator())
                    .add(AbstractMongoDbDiagnosticService.bsonType(item), documentPaths.add(itemPath));
            }
            index++;
        }
    }

    private static final class FieldAccumulator {
        private final Set<String> types = new TreeSet<>();
        private int documentCount;

        void add(String type, boolean firstSeenInDocument) {
            types.add(type);
            if (firstSeenInDocument) {
                documentCount++;
            }
        }

        MongoDbModels.SchemaField toField(String path, int samples) {
            String frequency = samples == 0 ? "0%" : Math.round((documentCount * 100.0d) / samples) + "%";
            return new MongoDbModels.SchemaField(path, new ArrayList<>(types).stream().sorted(Comparator.naturalOrder()).toList(), documentCount, frequency);
        }
    }
}
