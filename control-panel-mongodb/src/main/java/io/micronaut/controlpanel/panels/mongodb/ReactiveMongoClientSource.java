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
import com.mongodb.connection.ClusterDescription;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The clients of the MongoDB reactive streams driver. Results are awaited for at most {@link MongoDbInspector#TIMEOUT}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = MongoClient.class)
final class ReactiveMongoClientSource implements MongoClientSource {

    private final BeanContext beanContext;

    ReactiveMongoClientSource(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<Client> clients() {
        return beanContext.getBeanRegistrations(MongoClient.class).stream()
            .map(registration -> (Client) new ReactiveClient(MongoDbInspector.clientName(registration), registration.getBean()))
            .toList();
    }

    /**
     * @param publisher the publisher
     * @param limit the maximum number of items
     * @param <T> the item type
     * @return up to {@code limit} items
     */
    static <T> List<T> collect(Publisher<T> publisher, int limit) {
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<>() {
            private final List<T> items = new ArrayList<>();
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(limit);
            }

            @Override
            public void onNext(T item) {
                items.add(item);
                if (items.size() >= limit) {
                    subscription.cancel();
                    result.complete(items);
                }
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                result.complete(items);
            }
        });
        try {
            return result.get(MongoDbInspector.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out", e);
        }
    }

    private record ReactiveClient(String name, MongoClient client) implements Client {

        @Override
        public String driver() {
            return "reactive";
        }

        @Override
        public ClusterDescription cluster() {
            return client.getClusterDescription();
        }

        @Override
        public List<Document> listDatabases(int limit) {
            return collect(client.listDatabases().maxTime(MongoDbInspector.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), limit);
        }

        @Override
        public List<Document> listCollections(String database, int limit) {
            return collect(client.getDatabase(database).listCollections().maxTime(MongoDbInspector.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), limit);
        }

        @Override
        public long estimatedDocumentCount(String database, String collection) {
            List<Long> count = collect(client.getDatabase(database).getCollection(collection)
                .estimatedDocumentCount(new EstimatedDocumentCountOptions().maxTime(MongoDbInspector.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)), 1);
            return count.isEmpty() ? 0 : count.getFirst();
        }
    }
}
