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

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;
import java.util.Map;

/**
 * Body models used by the MongoDB Control Panel templates.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
final class MongoDbModels {

    private MongoDbModels() {
    }

    @ReflectiveAccess
    record Body(ClientSummary client,
                List<DatabaseInfo> databases,
                List<DiagnosticError> errors,
                boolean schemaEnabled,
                boolean explainEnabled) {
        static Body empty() {
            return new Body(
                new ClientSummary("none", "none", "", List.of(), Map.of(), "", "", "", "", "", false, 0, 0, 0, 0, 0, "", "", false),
                List.of(),
                List.of(new DiagnosticError("clients", "No supported MongoDB client beans were detected.")),
                false,
                false
            );
        }
    }

    @ReflectiveAccess
    record ClientSummary(String beanName,
                         String mode,
                         String configuredDatabase,
                         List<String> hosts,
                         Map<String, String> options,
                         String readPreference,
                         String readConcern,
                         String writeConcern,
                         String uuidRepresentation,
                         String applicationName,
                         boolean sslEnabled,
                         int codecCount,
                         int codecRegistryCount,
                         int commandListenerCount,
                         int connectionPoolListenerCount,
                         int packageCount,
                         String poolSummary,
                         String buildVersion,
                         boolean buildOk) {
    }

    @ReflectiveAccess
    record DatabaseInfo(String name, int collectionCount, List<CollectionInfo> collections, List<DiagnosticError> errors) {
    }

    @ReflectiveAccess
    record CollectionInfo(String name,
                          String type,
                          String estimatedDocumentCount,
                          String optionsJson,
                          String validationJson,
                          List<IndexInfo> indexes,
                          SchemaSummary schema,
                          List<DiagnosticError> errors) {
    }

    @ReflectiveAccess
    record IndexInfo(String name,
                     String keysJson,
                     boolean unique,
                     boolean sparse,
                     String expireAfterSeconds,
                     String partialFilterJson,
                     String collationJson,
                     String optionsJson) {
    }

    @ReflectiveAccess
    record SchemaSummary(boolean enabled,
                         int sampledDocuments,
                         boolean truncated,
                         List<SchemaField> fields,
                         List<DiagnosticError> errors) {
        static SchemaSummary disabled() {
            return new SchemaSummary(false, 0, false, List.of(), List.of());
        }
    }

    @ReflectiveAccess
    record SchemaField(String path, List<String> types, int count, String frequency) {
    }

    @ReflectiveAccess
    record DiagnosticError(String scope, String message) {
    }
}
