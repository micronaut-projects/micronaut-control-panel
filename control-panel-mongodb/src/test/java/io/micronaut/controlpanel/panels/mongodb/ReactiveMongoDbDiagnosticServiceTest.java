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
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.ListCollectionsPublisher;
import com.mongodb.reactivestreams.client.ListDatabasesPublisher;
import com.mongodb.reactivestreams.client.ListIndexesPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactiveMongoDbDiagnosticServiceTest {

    @Test
    void readsReactiveCollectionMetadata() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase admin = mock(MongoDatabase.class);
        MongoDatabase demo = mock(MongoDatabase.class);
        MongoCollection<Document> books = mock(MongoCollection.class);
        var databases = databasesPublisher(List.of(new Document("name", "demo")));
        var collections = collectionsPublisher(List.of(new Document("name", "books").append("type", "collection")));
        var indexes = indexesPublisher(List.of(new Document("name", "_id_").append("key", new Document("_id", 1))));
        var samples = findPublisher(List.of(new Document("title", "Dune")));
        var configuration = new MongoDbControlPanelConfiguration();
        configuration.getSchema().setEnabled(true);

        when(client.getDatabase("admin")).thenReturn(admin);
        when(admin.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(admin);
        when(admin.runCommand(new Document("buildInfo", 1))).thenReturn(Mono.just(new Document("version", "8.0.0")));
        when(client.listDatabases(Document.class)).thenReturn(databases);
        when(client.getDatabase("demo")).thenReturn(demo);
        when(demo.getName()).thenReturn("demo");
        when(demo.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(demo);
        when(demo.listCollections(Document.class)).thenReturn(collections);
        when(demo.getCollection("books", Document.class)).thenReturn(books);
        when(books.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(books);
        when(books.estimatedDocumentCount(any(EstimatedDocumentCountOptions.class))).thenReturn(Mono.just(1L));
        when(books.listIndexes(Document.class)).thenReturn(indexes);
        when(books.find(Document.class)).thenReturn(samples);

        var service = new ReactiveMongoDbDiagnosticService("default", client, List.of(), configuration);

        var body = service.getBody();

        assertTrue(body.client().buildOk());
        assertEquals("reactive", body.client().mode());
        assertEquals("books", body.databases().get(0).collections().get(0).name());
        assertEquals("_id_", body.databases().get(0).collections().get(0).indexes().get(0).name());
        assertTrue(body.databases().get(0).collections().get(0).schema().enabled());
        verify(databases).maxTime(2_000, TimeUnit.MILLISECONDS);
        verify(books).estimatedDocumentCount(argThat(options -> options.getMaxTime(TimeUnit.MILLISECONDS) == 2_000));
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
        var databases = databasesPublisher(List.of(new Document("name", "demo")));
        var collectionsPublisher = collectionsPublisher(List.of(
            new Document("name", "books"),
            new Document("name", "authors")
        ));
        var booksIndexes = indexesPublisher(List.of());
        var authorsIndexes = indexesPublisher(List.of());
        var samples = findPublisher(List.of(new Document("title", "Dune")));

        when(client.getDatabase("admin")).thenReturn(admin);
        when(admin.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(admin);
        when(admin.runCommand(new Document("buildInfo", 1))).thenReturn(Mono.just(new Document("version", "8.0.0")));
        when(client.listDatabases(Document.class)).thenReturn(databases);
        when(client.getDatabase("demo")).thenReturn(demo);
        when(demo.getName()).thenReturn("demo");
        when(demo.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(demo);
        when(demo.listCollections(Document.class)).thenReturn(collectionsPublisher);
        when(demo.getCollection("books", Document.class)).thenReturn(books);
        when(demo.getCollection("authors", Document.class)).thenReturn(authors);
        when(books.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(books);
        when(authors.withTimeout(2_000, TimeUnit.MILLISECONDS)).thenReturn(authors);
        when(books.estimatedDocumentCount(any(EstimatedDocumentCountOptions.class))).thenReturn(Mono.just(1L));
        when(authors.estimatedDocumentCount(any(EstimatedDocumentCountOptions.class))).thenReturn(Mono.just(1L));
        when(books.listIndexes(Document.class)).thenReturn(booksIndexes);
        when(authors.listIndexes(Document.class)).thenReturn(authorsIndexes);
        when(books.find(Document.class)).thenReturn(samples);

        var service = new ReactiveMongoDbDiagnosticService("default", client, List.of(), configuration);

        var collections = service.getBody().databases().get(0).collections();

        assertTrue(collections.get(0).schema().enabled());
        assertTrue(collections.get(0).schema().fields().stream().anyMatch(field -> field.path().equals("title")));
        assertTrue(collections.get(1).schema().fields().isEmpty());
        verify(books).find(Document.class);
        verify(authors, never()).find(Document.class);
    }

    private static ListDatabasesPublisher<Document> databasesPublisher(List<Document> documents) {
        ListDatabasesPublisher<Document> publisher = mock(ListDatabasesPublisher.class);
        when(publisher.nameOnly(true)).thenReturn(publisher);
        when(publisher.authorizedDatabasesOnly(true)).thenReturn(publisher);
        when(publisher.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(publisher);
        when(publisher.batchSize(anyInt())).thenReturn(publisher);
        doAnswer(invocation -> {
            Flux.fromIterable(documents).subscribe((Subscriber<? super Document>) invocation.getArgument(0));
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    private static ListCollectionsPublisher<Document> collectionsPublisher(List<Document> documents) {
        ListCollectionsPublisher<Document> publisher = mock(ListCollectionsPublisher.class);
        when(publisher.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            Flux.fromIterable(documents).subscribe((Subscriber<? super Document>) invocation.getArgument(0));
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    private static ListIndexesPublisher<Document> indexesPublisher(List<Document> documents) {
        ListIndexesPublisher<Document> publisher = mock(ListIndexesPublisher.class);
        when(publisher.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            Flux.fromIterable(documents).subscribe((Subscriber<? super Document>) invocation.getArgument(0));
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    private static FindPublisher<Document> findPublisher(List<Document> documents) {
        FindPublisher<Document> publisher = mock(FindPublisher.class);
        when(publisher.limit(anyInt())).thenReturn(publisher);
        when(publisher.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            Flux.fromIterable(documents).subscribe((Subscriber<? super Document>) invocation.getArgument(0));
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }
}
