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

import com.mongodb.connection.ClusterDescription;
import io.micronaut.core.annotation.Internal;
import org.bson.Document;

import java.util.List;

/**
 * Gives the MongoDB inspector access to the clients of one driver, sync or reactive, so that the inspector does not
 * depend on either.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Internal
interface MongoClientSource {

    /**
     * @return the clients of this driver
     */
    List<Client> clients();

    /**
     * A MongoDB client. Every method that queries the server stops after {@link MongoDbInspector#TIMEOUT}.
     */
    interface Client {

        /**
         * @return the client name
         */
        String name();

        /**
         * @return {@code sync} or {@code reactive}
         */
        String driver();

        /**
         * @return the cluster description the driver keeps in memory
         */
        ClusterDescription cluster();

        /**
         * @param limit the maximum number of databases
         * @return the {@code listDatabases} documents
         */
        List<Document> listDatabases(int limit);

        /**
         * @param database the database name
         * @param limit the maximum number of collections
         * @return the {@code listCollections} documents
         */
        List<Document> listCollections(String database, int limit);

        /**
         * @param database the database name
         * @param collection the collection name
         * @return the estimated number of documents
         */
        long estimatedDocumentCount(String database, String collection);
    }
}
