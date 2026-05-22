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
package example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ValidationOptions;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Loads sample MongoDB data for the optional example application MongoDB environment.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Singleton
@Requires(env = "mongodb")
final class MongoDbSampleDataLoader implements ApplicationEventListener<ServerStartupEvent> {

    private final MongoClient defaultClient;
    private final MongoClient inventoryClient;

    MongoDbSampleDataLoader(@Named("default") MongoClient defaultClient,
                            @Named("inventory") MongoClient inventoryClient) {
        this.defaultClient = defaultClient;
        this.inventoryClient = inventoryClient;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        loadLibrary();
        loadInventory();
    }

    private void loadLibrary() {
        var database = defaultClient.getDatabase("library");
        if (!collectionExists(database.listCollectionNames().into(new ArrayList<>()), "books")) {
            database.createCollection("books", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                    .validator(new Document("$jsonSchema", new Document("required", List.of("title", "isbn"))))));
        }
        var books = database.getCollection("books");
        if (books.estimatedDocumentCount() == 0) {
            books.insertMany(List.of(
                new Document("title", "Dune").append("isbn", "9780441172719").append("copies", 3),
                new Document("title", "The Left Hand of Darkness").append("isbn", "9780441478125").append("copies", 2)
            ));
        }
        books.createIndex(Indexes.ascending("isbn"), new IndexOptions().unique(true).name("isbn_1"));
        books.createIndex(Indexes.ascending("expiresAt"), new IndexOptions().expireAfter(3600L, TimeUnit.SECONDS).name("expiresAt_1"));
    }

    private void loadInventory() {
        var database = inventoryClient.getDatabase("inventory");
        if (!collectionExists(database.listCollectionNames().into(new ArrayList<>()), "products")) {
            database.createCollection("products", new CreateCollectionOptions()
                .validationOptions(new ValidationOptions()
                    .validator(new Document("$jsonSchema", new Document("required", List.of("sku", "name"))))));
        }
        var products = database.getCollection("products");
        if (products.estimatedDocumentCount() == 0) {
            products.insertMany(List.of(
                new Document("sku", "BK-001").append("name", "Notebook").append("quantity", 10),
                new Document("sku", "PN-001").append("name", "Pen").append("quantity", 25)
            ));
        }
        products.createIndex(Indexes.ascending("sku"), new IndexOptions().unique(true).name("sku_1"));
    }

    private static boolean collectionExists(List<String> names, String name) {
        return names.contains(name);
    }
}
