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

import com.mongodb.client.model.EstimatedDocumentCountOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import org.bson.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@EachBean(MongoClient.class)
@Requires(classes = MongoClient.class)
final class ReactiveMongoDbDiagnosticService extends AbstractMongoDbDiagnosticService {

    private final MongoClient client;

    ReactiveMongoDbDiagnosticService(@Parameter String beanName,
                                     @Parameter MongoClient client,
                                     Collection<AbstractMongoConfiguration> configurations,
                                     MongoDbControlPanelConfiguration configuration) {
        super(beanName, "reactive", configurations, configuration);
        this.client = client;
    }

    @Override
    public MongoDbModels.Body getBody() {
        List<MongoDbModels.DiagnosticError> errors = new ArrayList<>();
        String buildVersion = "";
        boolean buildOk = false;
        try {
            var buildInfo = single(client.getDatabase("admin")
                .withTimeout(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                .runCommand(new Document("buildInfo", 1)));
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
            return many(client.listDatabases(Document.class)
                .nameOnly(true)
                .authorizedDatabasesOnly(true)
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
                .batchSize(positiveLimit(configuration.getMaxDatabases())), positiveLimit(configuration.getMaxDatabases()))
                .stream()
                .map(databaseInfo -> databaseInfo(databaseName(databaseInfo), schemaSamplingBudget))
                .toList();
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
            collections = many(database.listCollections(Document.class)
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS), positiveLimit(configuration.getMaxCollectionsPerDatabase()))
                .stream()
                .map(collectionInfo -> collectionInfo(database, collectionInfo, schemaSamplingBudget))
                .toList();
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
            count = String.valueOf(single(collection.estimatedDocumentCount(new EstimatedDocumentCountOptions()
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS))));
        } catch (RuntimeException e) {
            errors.addAll(error(COLLECTION_ERROR_PREFIX + database.getName() + "." + name + " count", e));
        }
        List<MongoDbModels.IndexInfo> indexes = List.of();
        try {
            indexes = indexes(many(collection.listIndexes(Document.class)
                .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS), positiveLimit(configuration.getMaxIndexesPerCollection())));
        } catch (RuntimeException e) {
            errors.addAll(error(COLLECTION_ERROR_PREFIX + database.getName() + "." + name + " indexes", e));
        }
        MongoDbModels.SchemaSummary schema = schemaAnalyzer.disabled();
        if (schemaSamplingBudget.tryAcquire()) {
            try {
                schema = schemaAnalyzer.summarize(many(collection.find(Document.class)
                    .limit(positiveLimit(configuration.getSchema().getSampleSize()))
                    .maxTime(timeout(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS), positiveLimit(configuration.getSchema().getSampleSize())));
            } catch (RuntimeException e) {
                schema = new MongoDbModels.SchemaSummary(true, 0, false, List.of(), error(COLLECTION_ERROR_PREFIX + database.getName() + "." + name + " schema", e));
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

    private <T> T single(org.reactivestreams.Publisher<T> publisher) {
        return Objects.requireNonNull(Mono.from(publisher)
            .timeout(timeoutDuration())
            .block(timeoutDuration()));
    }

    private <T> List<T> many(org.reactivestreams.Publisher<T> publisher, int limit) {
        return Objects.requireNonNull(Flux.from(publisher)
            .timeout(timeoutDuration())
            .take(limit)
            .collectList()
            .block(timeoutDuration()));
    }

    private Duration timeoutDuration() {
        return Duration.ofMillis(timeoutMillis());
    }
}
