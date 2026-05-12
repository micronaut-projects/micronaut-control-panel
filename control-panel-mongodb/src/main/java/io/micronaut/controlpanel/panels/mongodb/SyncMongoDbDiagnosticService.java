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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.EstimatedDocumentCountOptions;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@EachBean(MongoClient.class)
@Requires(classes = MongoClient.class)
final class SyncMongoDbDiagnosticService extends AbstractMongoDbDiagnosticService {

    private final MongoClient client;

    SyncMongoDbDiagnosticService(@Parameter String beanName,
                                 @Parameter MongoClient client,
                                 Collection<AbstractMongoConfiguration> configurations,
                                 MongoDbControlPanelConfiguration configuration) {
        super(beanName, "sync", configurations, configuration);
        this.client = client;
    }

    @Override
    public MongoDbModels.Body getBody() {
        List<MongoDbModels.DiagnosticError> errors = new ArrayList<>();
        String buildVersion = "";
        boolean buildOk = false;
        try {
            var buildInfo = client.getDatabase("admin")
                .withTimeout(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                .runCommand(new Document("buildInfo", 1));
            buildVersion = buildVersion(buildInfo);
            buildOk = true;
        } catch (RuntimeException e) {
            errors.addAll(error("buildInfo", e));
        }
        var schemaSamplingBudget = new SchemaSamplingBudget(
            configuration.getSchema().isEnabled(),
            nonNegativeLimit(configuration.getSchema().getMaxCollections())
        );
        return new MongoDbModels.Body(
            clientSummary(buildVersion, buildOk),
            databases(errors, schemaSamplingBudget),
            errors,
            configuration.getSchema().isEnabled(),
            configuration.getExplain().isEnabled()
        );
    }

    private List<MongoDbModels.DatabaseInfo> databases(List<MongoDbModels.DiagnosticError> rootErrors,
                                                       SchemaSamplingBudget schemaSamplingBudget) {
        try {
            List<MongoDbModels.DatabaseInfo> result = new ArrayList<>();
            for (Document databaseInfo : client.listDatabases(Document.class)
                .nameOnly(true)
                .authorizedDatabasesOnly(true)
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                .batchSize(positiveLimit(configuration.getMaxDatabases()))) {
                result.add(databaseInfo(databaseName(databaseInfo), schemaSamplingBudget));
                if (result.size() >= positiveLimit(configuration.getMaxDatabases())) {
                    break;
                }
            }
            return result;
        } catch (RuntimeException e) {
            rootErrors.addAll(error("databases", e));
            return List.of();
        }
    }

    private MongoDbModels.DatabaseInfo databaseInfo(String databaseName, SchemaSamplingBudget schemaSamplingBudget) {
        List<MongoDbModels.DiagnosticError> errors = new ArrayList<>();
        List<MongoDbModels.CollectionInfo> collections = new ArrayList<>();
        MongoDatabase database = client.getDatabase(databaseName)
            .withTimeout(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        try {
            for (Document collectionInfo : database.listCollections(Document.class)
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                .batchSize(positiveLimit(configuration.getMaxCollectionsPerDatabase()))) {
                collections.add(collectionInfo(database, collectionInfo, schemaSamplingBudget));
                if (collections.size() >= positiveLimit(configuration.getMaxCollectionsPerDatabase())) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            errors.addAll(error("database " + databaseName, e));
        }
        return new MongoDbModels.DatabaseInfo(databaseName, collections.size(), collections, errors);
    }

    private MongoDbModels.CollectionInfo collectionInfo(MongoDatabase database,
                                                        Document collectionInfo,
                                                        SchemaSamplingBudget schemaSamplingBudget) {
        String name = collectionName(collectionInfo);
        MongoCollection<Document> collection = database.getCollection(name, Document.class)
            .withTimeout(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
        List<MongoDbModels.DiagnosticError> errors = new ArrayList<>();
        String count = "";
        try {
            count = String.valueOf(collection.estimatedDocumentCount(new EstimatedDocumentCountOptions()
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)));
        } catch (RuntimeException e) {
            errors.addAll(error("collection " + database.getName() + "." + name + " count", e));
        }
        List<MongoDbModels.IndexInfo> indexes = List.of();
        try {
            indexes = indexes(limitDocuments(collection.listIndexes(Document.class)
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS), positiveLimit(configuration.getMaxIndexesPerCollection())));
        } catch (RuntimeException e) {
            errors.addAll(error("collection " + database.getName() + "." + name + " indexes", e));
        }
        MongoDbModels.SchemaSummary schema = schemaAnalyzer.disabled();
        if (schemaSamplingBudget.tryAcquire()) {
            try {
                schema = schemaAnalyzer.summarize(limitDocuments(collection.find(Document.class)
                    .limit(positiveLimit(configuration.getSchema().getSampleSize()))
                    .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS), positiveLimit(configuration.getSchema().getSampleSize())));
            } catch (RuntimeException e) {
                schema = new MongoDbModels.SchemaSummary(true, 0, false, List.of(), error("collection " + database.getName() + "." + name + " schema", e));
            }
        }
        return new MongoDbModels.CollectionInfo(
            name,
            collectionType(collectionInfo),
            count,
            sanitizer.toJson(redactedCollectionOptions(collectionInfo)),
            validationJson(collectionInfo),
            indexes,
            schema,
            errors
        );
    }
}
