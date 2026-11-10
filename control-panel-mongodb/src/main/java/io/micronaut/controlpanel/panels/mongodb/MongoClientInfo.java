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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A MongoDB client. Internal to the MongoDB panel views.
 *
 * @param name the client name: {@code default}, or the name under {@code mongodb.servers}
 * @param driver {@code sync} or {@code reactive}
 * @param clusterType the cluster type the driver detected, such as {@code STANDALONE} or {@code REPLICA_SET}
 * @param servers the servers the driver knows about
 * @param databases the databases, or {@code null} when they were not listed
 * @param databasesTruncated whether there are more databases than the panel lists
 * @param error why the databases could not be listed, if they could not
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record MongoClientInfo(
    String name,
    String driver,
    String clusterType,
    List<Server> servers,
    @Nullable List<Database> databases,
    boolean databasesTruncated,
    @Nullable String error
) {

    /**
     * @return whether at least one server is connected
     */
    public boolean connected() {
        return servers.stream().anyMatch(Server::connected);
    }

    /**
     * A server, as the driver sees it.
     *
     * @param address the host and port
     * @param type the server type, such as {@code STANDALONE} or {@code REPLICA_SET_PRIMARY}
     * @param connected whether the driver is connected to it
     * @param roundTripMillis the average round trip time, in milliseconds, when connected
     * @param replicaSet the replica set name, if any
     */
    @ReflectiveAccess
    public record Server(String address, String type, boolean connected, @Nullable Long roundTripMillis, @Nullable String replicaSet) {
    }

    /**
     * A database.
     *
     * @param name the database name
     * @param sizeOnDisk the size on disk, formatted
     * @param collections the collections and views, or {@code null} when they could not be listed
     * @param collectionsTruncated whether there are more collections than the panel lists
     */
    @ReflectiveAccess
    public record Database(String name, String sizeOnDisk, @Nullable List<Collection> collections, boolean collectionsTruncated) {
    }

    /**
     * A collection or view.
     *
     * @param name the name
     * @param type {@code collection}, {@code view} or {@code timeseries}
     * @param documents the estimated number of documents, for collections
     */
    @ReflectiveAccess
    public record Collection(String name, String type, @Nullable Long documents) {

        /**
         * @return the estimated number of documents, as text, or {@code null} if unknown
         */
        public @Nullable String documentsLabel() {
            if (documents == null) {
                return null;
            }
            return "~" + documents + (documents == 1 ? " document" : " documents");
        }
    }
}
