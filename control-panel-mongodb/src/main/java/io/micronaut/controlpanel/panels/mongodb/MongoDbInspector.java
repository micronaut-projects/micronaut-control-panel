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
import com.mongodb.connection.ServerConnectionState;
import com.mongodb.connection.ServerDescription;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects the MongoDB clients of the application, the servers they see, and their databases and collections.
 *
 * <p>Connection strings are never read, since they can carry credentials. Every query stops after {@link #TIMEOUT}
 * and the number of databases and collections is capped. Nothing is queried when the driver has no connected server.
 * Errors are shown by type only, since driver messages can include credentials or data; the full error is logged at
 * {@code DEBUG} level.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
public class MongoDbInspector {

    static final Duration TIMEOUT = Duration.ofSeconds(2);
    static final int MAX_DATABASES = 50;
    static final int MAX_COLLECTIONS = 100;

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbInspector.class);
    private static final String DEFAULT_NAME = "default";

    private final List<MongoClientSource> sources;

    /**
     * @param sources the client sources of the drivers on the classpath
     */
    MongoDbInspector(List<MongoClientSource> sources) {
        this.sources = sources;
    }

    /**
     * @param withDatabases whether to list the databases and collections, which queries the servers
     * @return the clients, sorted by name and driver
     */
    public List<MongoClientInfo> inspect(boolean withDatabases) {
        return sources.stream()
            .flatMap(source -> source.clients().stream())
            .sorted(Comparator.comparing(MongoClientSource.Client::name).thenComparing(MongoClientSource.Client::driver))
            .map(client -> inspect(client, withDatabases))
            .toList();
    }

    static String clientName(BeanRegistration<?> registration) {
        String name = registration.getIdentifier().getName();
        return name == null || name.isBlank() || "primary".equalsIgnoreCase(name) ? DEFAULT_NAME : name;
    }

    private static MongoClientInfo inspect(MongoClientSource.Client client, boolean withDatabases) {
        boolean connected = withDatabases && awaitConnection(client);
        ClusterDescription cluster = client.cluster();
        List<MongoClientInfo.Server> servers = cluster.getServerDescriptions().stream()
            .map(MongoDbInspector::server)
            .sorted(Comparator.comparing(MongoClientInfo.Server::address))
            .toList();
        String clusterType = String.valueOf(cluster.getType());
        if (!withDatabases) {
            return new MongoClientInfo(client.name(), client.driver(), clusterType, servers, null, false, null);
        }
        if (!connected) {
            return new MongoClientInfo(client.name(), client.driver(), clusterType, servers, null, false,
                "The driver is not connected to any server, so databases are not listed.");
        }
        try {
            List<Document> databases = client.listDatabases(MAX_DATABASES + 1);
            List<MongoClientInfo.Database> result = new ArrayList<>();
            for (Document database : databases.subList(0, Math.min(databases.size(), MAX_DATABASES))) {
                result.add(database(client, database));
            }
            return new MongoClientInfo(client.name(), client.driver(), clusterType, servers, result, databases.size() > MAX_DATABASES, null);
        } catch (RuntimeException e) {
            LOG.debug("Unable to list the databases of MongoDB client '{}'", client.name(), e);
            return new MongoClientInfo(client.name(), client.driver(), clusterType, servers, null, false,
                "The databases could not be listed (" + e.getClass().getSimpleName() + "). The application log has the error at DEBUG level.");
        }
    }

    /**
     * Waits for up to {@link #TIMEOUT} for the driver to connect to a server. A client that has just been created is
     * still connecting, and querying a cluster without a connected server blocks for the whole server selection
     * timeout, which defaults to 30 seconds.
     */
    private static boolean awaitConnection(MongoClientSource.Client client) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (client.cluster().getServerDescriptions().stream().anyMatch(server -> server.getState() == ServerConnectionState.CONNECTED)) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static MongoClientInfo.Database database(MongoClientSource.Client client, Document database) {
        String name = database.getString("name");
        String size = formatSize(database.get("sizeOnDisk") instanceof Number number ? number.longValue() : null);
        try {
            List<Document> collections = client.listCollections(name, MAX_COLLECTIONS + 1);
            List<MongoClientInfo.Collection> result = collections.subList(0, Math.min(collections.size(), MAX_COLLECTIONS)).stream()
                .map(collection -> collection(client, name, collection))
                .sorted(Comparator.comparing(MongoClientInfo.Collection::name))
                .toList();
            return new MongoClientInfo.Database(name, size, result, collections.size() > MAX_COLLECTIONS);
        } catch (RuntimeException e) {
            LOG.debug("Unable to list the collections of MongoDB database '{}' of client '{}'", name, client.name(), e);
            return new MongoClientInfo.Database(name, size, null, false);
        }
    }

    private static MongoClientInfo.Collection collection(MongoClientSource.Client client, String database, Document collection) {
        String name = collection.getString("name");
        String type = collection.get("type") instanceof String value ? value : "collection";
        Long documents = null;
        if (!"view".equals(type)) {
            try {
                documents = client.estimatedDocumentCount(database, name);
            } catch (RuntimeException e) {
                LOG.debug("Unable to count the documents of MongoDB collection '{}.{}'", database, name, e);
            }
        }
        return new MongoClientInfo.Collection(name, type, documents);
    }

    private static MongoClientInfo.Server server(ServerDescription server) {
        boolean connected = server.getState() == ServerConnectionState.CONNECTED;
        return new MongoClientInfo.Server(
            server.getAddress().toString(),
            String.valueOf(server.getType()),
            connected,
            connected ? TimeUnit.NANOSECONDS.toMillis(server.getRoundTripTimeNanos()) : null,
            server.getSetName()
        );
    }

    static String formatSize(@Nullable Long bytes) {
        if (bytes == null) {
            return "unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
