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

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationOptions;
import io.micronaut.context.ApplicationContext;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDbLiveIntegrationTest {

    @Test
    void discoversMicronautCreatedDefaultAndNamedClientsAgainstLiveMongoDb() {
        try (MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet()) {
            mongo.start();
            seedDefaultDatabase(mongo.getReplicaSetUrl("library"));
            seedInventoryDatabase(mongo.getReplicaSetUrl("inventory"));

            try (ApplicationContext context = ApplicationContext.run(Map.of(
                "mongodb.servers.default.uri", mongo.getReplicaSetUrl("library"),
                "mongodb.servers.inventory.uri", mongo.getReplicaSetUrl("inventory"),
                "micronaut.control-panel.panels.mongodb.schema.enabled", true,
                "micronaut.control-panel.panels.mongodb.schema.sample-size", 10,
                "micronaut.control-panel.panels.mongodb.schema.max-collections", 4,
                "micronaut.control-panel.panels.mongodb.max-databases", 10,
                "micronaut.control-panel.panels.mongodb.max-collections-per-database", 20,
                "micronaut.control-panel.panels.mongodb.max-indexes-per-collection", 20
            ))) {
                List<MongoDbDiagnosticService> services = context.getBeansOfType(MongoDbDiagnosticService.class)
                    .stream()
                    .sorted(Comparator.comparing(MongoDbDiagnosticService::mode).thenComparing(MongoDbDiagnosticService::beanName))
                    .toList();

                assertTrue(services.stream().anyMatch(service -> service.mode().equals("sync") && service.beanName().equals("default")), serviceNames(services));
                assertTrue(services.stream().anyMatch(service -> service.mode().equals("sync") && service.beanName().equals("inventory")), serviceNames(services));
                assertTrue(services.stream().anyMatch(service -> service.mode().equals("reactive") && service.beanName().equals("default")), serviceNames(services));
                assertTrue(services.stream().anyMatch(service -> service.mode().equals("reactive") && service.beanName().equals("inventory")), serviceNames(services));

                List<String> panelNames = context.getBeansOfType(MongoDbControlPanel.class)
                    .stream()
                    .map(MongoDbControlPanel::getName)
                    .sorted()
                    .toList();
                assertEquals(List.of(
                    "mongodb-reactive-default",
                    "mongodb-reactive-inventory",
                    "mongodb-sync-default",
                    "mongodb-sync-inventory"
                ), panelNames);

                MongoDbModels.Body syncDefault = body(services, "sync", "default");
                assertTrue(syncDefault.client().buildOk());
                assertEquals("library", syncDefault.client().configuredDatabase());
                MongoDbModels.CollectionInfo books = collection(syncDefault, "library", "books");
                assertEquals("2", books.estimatedDocumentCount());
                assertTrue(books.validationJson().contains("$jsonSchema"));
                assertTrue(books.indexes().stream().anyMatch(index -> index.name().equals("isbn_1") && index.unique()));
                assertTrue(books.indexes().stream().anyMatch(index -> index.name().equals("expiresAt_1") && index.expireAfterSeconds().equals("3600")));
                assertFalse(books.schema().enabled());
                assertTrue(books.errors().isEmpty());

                MongoDbModels.Body syncInventory = body(services, "sync", "inventory");
                assertTrue(syncInventory.client().buildOk());
                assertEquals("inventory", syncInventory.client().configuredDatabase());
                MongoDbModels.CollectionInfo products = collection(syncInventory, "inventory", "products");
                assertTrue(products.indexes().stream().anyMatch(index -> index.name().equals("sku_1") && index.unique()));
                assertTrue(products.validationJson().contains("$jsonSchema"));
                assertTrue(products.errors().isEmpty());

                MongoDbModels.Body reactiveDefault = body(services, "reactive", "default");
                assertTrue(reactiveDefault.client().buildOk());
                assertNotNull(collection(reactiveDefault, "library", "books"));
                assertTrue(reactiveDefault.errors().isEmpty());
            }
        }
    }

    private static MongoDbModels.Body body(List<MongoDbDiagnosticService> services, String mode, String beanName) {
        return services.stream()
            .filter(service -> service.mode().equals(mode) && service.beanName().equals(beanName))
            .findFirst()
            .orElseThrow()
            .getBody();
    }

    private static String serviceNames(List<MongoDbDiagnosticService> services) {
        return services.stream()
            .map(service -> service.mode() + ":" + service.beanName())
            .toList()
            .toString();
    }

    private static MongoDbModels.CollectionInfo collection(MongoDbModels.Body body, String databaseName, String collectionName) {
        return body.databases()
            .stream()
            .filter(database -> database.name().equals(databaseName))
            .findFirst()
            .orElseThrow()
            .collections()
            .stream()
            .filter(collection -> collection.name().equals(collectionName))
            .findFirst()
            .orElseThrow();
    }

    private static void seedDefaultDatabase(String uri) {
        try (var client = MongoClients.create(uri)) {
            var database = client.getDatabase("library");
            database.createCollection("books", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                    .validator(new Document("$jsonSchema", new Document("required", List.of("title", "isbn"))))));
            var books = database.getCollection("books");
            books.insertMany(List.of(
                new Document("title", "Dune").append("isbn", "9780441172719").append("copies", 3),
                new Document("title", "The Left Hand of Darkness").append("isbn", "9780441478125").append("copies", 2)
            ));
            books.createIndex(Indexes.ascending("isbn"), new IndexOptions().unique(true));
            books.createIndex(Indexes.ascending("expiresAt"), new IndexOptions().expireAfter(3600L, TimeUnit.SECONDS));
        }
    }

    private static void seedInventoryDatabase(String uri) {
        try (var client = MongoClients.create(uri)) {
            var database = client.getDatabase("inventory");
            database.createCollection("products", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                    .validator(new Document("$jsonSchema", new Document("required", List.of("sku", "name"))))));
            var products = database.getCollection("products");
            products.insertMany(List.of(
                new Document("sku", "BK-001").append("name", "Notebook").append("quantity", 10),
                new Document("sku", "PN-001").append("name", "Pen").append("quantity", 25)
            ));
            products.createIndex(Indexes.ascending("sku"), new IndexOptions().unique(true));
        }
    }
}
