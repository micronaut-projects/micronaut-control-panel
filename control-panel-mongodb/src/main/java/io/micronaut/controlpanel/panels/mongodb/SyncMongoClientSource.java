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
import com.mongodb.client.MongoCluster;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoIterable;
import com.mongodb.connection.ClusterDescription;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The clients of the MongoDB sync driver.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = MongoClient.class)
final class SyncMongoClientSource implements MongoClientSource {

    private final BeanContext beanContext;

    SyncMongoClientSource(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<Client> clients() {
        return beanContext.getBeanRegistrations(MongoClient.class).stream()
            .map(registration -> (Client) new SyncClient(MongoDbInspector.clientName(registration), registration.getBean()))
            .toList();
    }

    private record SyncClient(String name, MongoClient client) implements Client {

        @Override
        public String driver() {
            return "sync";
        }

        @Override
        public ClusterDescription cluster() {
            return client.getClusterDescription();
        }

        /**
         * @return the client with a timeout that bounds server selection, socket I/O and the server-side time limit
         * of each operation, so that a replica set without a primary does not hold the page for the server selection
         * timeout
         */
        private MongoCluster bounded() {
            return client.withTimeout(MongoDbInspector.TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public List<Document> listDatabases(int limit) {
            return take(bounded().listDatabases(), limit);
        }

        @Override
        public List<Document> listCollections(String database, int limit) {
            return take(bounded().getDatabase(database).listCollections(), limit);
        }

        private static List<Document> take(MongoIterable<Document> iterable, int limit) {
            List<Document> documents = new ArrayList<>();
            try (MongoCursor<Document> cursor = iterable.batchSize(limit).iterator()) {
                while (documents.size() < limit && cursor.hasNext()) {
                    documents.add(cursor.next());
                }
            }
            return documents;
        }

        @Override
        public long estimatedDocumentCount(String database, String collection) {
            return bounded().getDatabase(database).getCollection(collection).estimatedDocumentCount();
        }
    }
}
