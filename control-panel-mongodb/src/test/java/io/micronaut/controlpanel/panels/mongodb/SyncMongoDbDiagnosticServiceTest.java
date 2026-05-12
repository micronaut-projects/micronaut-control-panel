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

import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListCollectionsIterable;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.EstimatedDocumentCountOptions;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncMongoDbDiagnosticServiceTest {

    @Test
    void readsCollectionValidationAndIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase admin = mock(MongoDatabase.class);
        MongoDatabase demo = mock(MongoDatabase.class);
        MongoCollection<Document> books = mock(MongoCollection.class);
        var configuration = new MongoDbControlPanelConfiguration();
        configuration.getSchema().setEnabled(true);
        var databases = databasesIterable(List.of(new Document("name", "demo")));
        var collectionInfos = collectionsIterable(List.of(
            new Document("name", "books")
                .append("type", "collection")
                .append("options", new Document("validator", new Document("$jsonSchema", new Document("required", List.of("title")))))
        ));
        var indexInfos = indexesIterable(List.of(
            new Document("name", "title_1").append("key", new Document("title", 1)).append("unique", true)
        ));
        var sampledDocuments = findIterable(List.of(
            new Document("title", "The Stand").append("copies", 3),
            new Document("title", "It").append("copies", 2)
        ));

        when(client.getDatabase("admin")).thenReturn(admin);
        when(admin.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(admin);
        when(admin.runCommand(new Document("buildInfo", 1))).thenReturn(new Document("version", "8.0.0"));
        when(client.listDatabases(Document.class)).thenReturn(databases);
        when(client.getDatabase("demo")).thenReturn(demo);
        when(demo.getName()).thenReturn("demo");
        when(demo.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(demo);
        when(demo.listCollections(Document.class)).thenReturn(collectionInfos);
        when(demo.getCollection("books", Document.class)).thenReturn(books);
        when(books.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(books);
        when(books.estimatedDocumentCount(any(EstimatedDocumentCountOptions.class))).thenReturn(2L);
        when(books.listIndexes(Document.class)).thenReturn(indexInfos);
        when(books.find(Document.class)).thenReturn(sampledDocuments);

        var service = new SyncMongoDbDiagnosticService("default", client, List.of(), configuration);

        var body = service.getBody();

        assertTrue(body.client().buildOk());
        assertEquals("8.0.0", body.client().buildVersion());
        assertEquals(1, body.databases().size());
        var collection = body.databases().get(0).collections().get(0);
        assertEquals("books", collection.name());
        assertEquals("2", collection.estimatedDocumentCount());
        assertTrue(collection.validationJson().contains("$jsonSchema"));
        assertEquals("title_1", collection.indexes().get(0).name());
        assertTrue(collection.indexes().get(0).unique());
        assertTrue(collection.schema().enabled());
        assertTrue(collection.schema().fields().stream().anyMatch(field -> field.path().equals("title")));
        verify(databases).maxTime(2_000, TimeUnit.MILLISECONDS);
        verify(books).estimatedDocumentCount(argThat(options -> options.getMaxTime(TimeUnit.MILLISECONDS) == 2_000));
    }

    @Test
    void unavailableClientProducesScopedError() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase admin = mock(MongoDatabase.class);
        when(client.getDatabase("admin")).thenReturn(admin);
        when(admin.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(admin);
        when(admin.runCommand(new Document("buildInfo", 1))).thenThrow(new MongoException("mongodb://user:secret@localhost failed"));
        when(client.listDatabases(Document.class)).thenThrow(new MongoException("not authorized"));

        var service = new SyncMongoDbDiagnosticService("default", client, List.of(), new MongoDbControlPanelConfiguration());

        var body = service.getBody();

        assertFalse(body.client().buildOk());
        assertEquals(2, body.errors().size());
        assertTrue(body.errors().stream().anyMatch(error -> error.scope().equals("buildInfo")));
        assertTrue(body.errors().stream().noneMatch(error -> error.message().contains("secret")));
    }

    @Test
    void schemaSamplingHonorsMaxCollections() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase admin = mock(MongoDatabase.class);
        MongoDatabase demo = mock(MongoDatabase.class);
        MongoCollection<Document> books = mock(MongoCollection.class);
        MongoCollection<Document> authors = mock(MongoCollection.class);
        var configuration = new MongoDbControlPanelConfiguration();
        configuration.getSchema().setEnabled(true);
        configuration.getSchema().setMaxCollections(1);
        var databases = databasesIterable(List.of(new Document("name", "demo")));
        var collectionInfos = collectionsIterable(List.of(
            new Document("name", "books"),
            new Document("name", "authors")
        ));
        var booksIndexes = indexesIterable(List.of());
        var authorsIndexes = indexesIterable(List.of());
        var samples = findIterable(List.of(new Document("title", "Dune")));

        when(client.getDatabase("admin")).thenReturn(admin);
        when(admin.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(admin);
        when(admin.runCommand(new Document("buildInfo", 1))).thenReturn(new Document("version", "8.0.0"));
        when(client.listDatabases(Document.class)).thenReturn(databases);
        when(client.getDatabase("demo")).thenReturn(demo);
        when(demo.getName()).thenReturn("demo");
        when(demo.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(demo);
        when(demo.listCollections(Document.class)).thenReturn(collectionInfos);
        when(demo.getCollection("books", Document.class)).thenReturn(books);
        when(demo.getCollection("authors", Document.class)).thenReturn(authors);
        when(books.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(books);
        when(authors.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(authors);
        when(books.estimatedDocumentCount(any(EstimatedDocumentCountOptions.class))).thenReturn(1L);
        when(authors.estimatedDocumentCount(any(EstimatedDocumentCountOptions.class))).thenReturn(1L);
        when(books.listIndexes(Document.class)).thenReturn(booksIndexes);
        when(authors.listIndexes(Document.class)).thenReturn(authorsIndexes);
        when(books.find(Document.class)).thenReturn(samples);

        var service = new SyncMongoDbDiagnosticService("default", client, List.of(), configuration);

        var collections = service.getBody().databases().get(0).collections();

        assertTrue(collections.get(0).schema().enabled());
        assertFalse(collections.get(1).schema().enabled());
        verify(books).find(Document.class);
        verify(authors, never()).find(Document.class);
    }

    private static ListDatabasesIterable<Document> databasesIterable(List<Document> documents) {
        ListDatabasesIterable<Document> iterable = mock(ListDatabasesIterable.class);
        when(iterable.nameOnly(true)).thenReturn(iterable);
        when(iterable.authorizedDatabasesOnly(true)).thenReturn(iterable);
        when(iterable.maxTime(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(iterable);
        when(iterable.batchSize(anyInt())).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor(documents));
        return iterable;
    }

    private static ListCollectionsIterable<Document> collectionsIterable(List<Document> documents) {
        ListCollectionsIterable<Document> iterable = mock(ListCollectionsIterable.class);
        when(iterable.maxTime(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(iterable);
        when(iterable.batchSize(anyInt())).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor(documents));
        return iterable;
    }

    private static ListIndexesIterable<Document> indexesIterable(List<Document> documents) {
        ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
        when(iterable.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor(documents));
        return iterable;
    }

    private static FindIterable<Document> findIterable(List<Document> documents) {
        FindIterable<Document> iterable = mock(FindIterable.class);
        when(iterable.limit(anyInt())).thenReturn(iterable);
        when(iterable.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor(documents));
        return iterable;
    }

    private static <T> MongoCursor<T> cursor(List<T> documents) {
        return new ListMongoCursor<>(documents);
    }

    private static final class ListMongoCursor<T> implements MongoCursor<T> {
        private final Iterator<T> iterator;

        private ListMongoCursor(List<T> values) {
            this.iterator = values.iterator();
        }

        @Override
        public void close() {
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return iterator.next();
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public T tryNext() {
            return hasNext() ? next() : null;
        }

        @Override
        public ServerCursor getServerCursor() {
            return null;
        }

        @Override
        public ServerAddress getServerAddress() {
            return null;
        }
    }
}
