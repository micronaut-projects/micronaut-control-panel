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
package io.micronaut.controlpanel.panels.neo4j;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.neo4j.bolt.Neo4jBoltConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Collects the URI, version, databases and graph size of the Neo4j server of the application.
 *
 * <p>The dashboard card only reads the configuration. The detail view runs a few read-only queries in parallel, in
 * read sessions with a transaction timeout, and gives up after {@link #TIMEOUT}: the server components, the database
 * list, the node and relationship counts (answered from Neo4j's count store), and the labels and relationship types.
 * Errors are shown by type or Neo4j status code only, and credentials are never shown.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = Neo4jBoltConfiguration.class)
public class Neo4jInspector {

    static final Duration TIMEOUT = Duration.ofSeconds(3);
    static final int MAX_LABELS = 50;

    private static final Logger LOG = LoggerFactory.getLogger(Neo4jInspector.class);
    private static final String SYSTEM_DATABASE = "system";

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public Neo4jInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @param detailed whether to query the server
     * @return the server
     */
    public Neo4jInfo inspect(boolean detailed) {
        Optional<Neo4jBoltConfiguration> configuration = configuration();
        String uri = configuration.map(c -> sanitize(c.getUri())).orElse("(not configured)");
        String authentication = configuration.map(Neo4jInspector::authentication).orElse("none");
        boolean driverOpen = !beanContext.getActiveBeanRegistrations(Qualifiers.byType(Driver.class)).isEmpty();
        if (!detailed) {
            return new Neo4jInfo(uri, authentication, driverOpen, null, List.of(), null, null);
        }
        Optional<Driver> driver = driver();
        if (driver.isEmpty()) {
            return new Neo4jInfo(uri, authentication, driverOpen, null, List.of(), null, "no Neo4j driver");
        }
        CompletableFuture<List<Record>> components = query(driver.get(), null,
            "CALL dbms.components() YIELD name, versions, edition RETURN name, versions[0] AS version, edition");
        CompletableFuture<List<Record>> databases = query(driver.get(), SYSTEM_DATABASE,
            "SHOW DATABASES YIELD name, currentStatus, default RETURN name, currentStatus, default");
        CompletableFuture<List<Record>> nodes = query(driver.get(), null, "MATCH (n) RETURN count(n) AS count");
        CompletableFuture<List<Record>> relationships = query(driver.get(), null, "MATCH ()-[r]->() RETURN count(r) AS count");
        CompletableFuture<List<Record>> labels = query(driver.get(), null,
            "CALL db.labels() YIELD label RETURN label ORDER BY label LIMIT " + MAX_LABELS);
        CompletableFuture<List<Record>> types = query(driver.get(), null,
            "CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType ORDER BY relationshipType LIMIT " + MAX_LABELS);
        try {
            CompletableFuture.allOf(components, databases, nodes, relationships, labels, types)
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return new Neo4jInfo(
                uri,
                authentication,
                true,
                server(components.join()),
                databases.join().stream()
                    .map(r -> new Neo4jInfo.Database(r.get("name").asString(), r.get("currentStatus").asString(""), r.get("default").asBoolean(false)))
                    .distinct()
                    .sorted(Comparator.comparing(Neo4jInfo.Database::name))
                    .toList(),
                new Neo4jInfo.Graph(
                    count(nodes.join()),
                    count(relationships.join()),
                    labels.join().stream().map(r -> r.get("label").asString()).toList(),
                    types.join().stream().map(r -> r.get("relationshipType").asString()).toList()
                ),
                null
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Neo4jInfo(uri, authentication, true, null, List.of(), null, "interrupted");
        } catch (TimeoutException e) {
            LOG.debug("Neo4j did not answer within {}", TIMEOUT);
            return new Neo4jInfo(uri, authentication, true, null, List.of(), null, "no answer within " + TIMEOUT.toSeconds() + "s");
        } catch (ExecutionException | RuntimeException e) {
            LOG.debug("Unable to read the Neo4j server", e);
            return new Neo4jInfo(uri, authentication, true, null, List.of(), null, describe(e));
        }
    }

    private Optional<Neo4jBoltConfiguration> configuration() {
        try {
            return beanContext.findBean(Neo4jBoltConfiguration.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the Neo4j configuration", e);
            return Optional.empty();
        }
    }

    private Optional<Driver> driver() {
        try {
            return beanContext.findBean(Driver.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to create the Neo4j driver", e);
            return Optional.empty();
        }
    }

    static String authentication(Neo4jBoltConfiguration configuration) {
        if (configuration.getAuthToken().isPresent()) {
            return "configured";
        }
        URI uri = configuration.getUri();
        return uri != null && uri.getUserInfo() != null ? "configured in the URI" : "none";
    }

    private static CompletableFuture<List<Record>> query(Driver driver, @Nullable String database, String cypher) {
        SessionConfig.Builder config = SessionConfig.builder().withDefaultAccessMode(AccessMode.READ);
        if (database != null) {
            config.withDatabase(database);
        }
        AsyncSession session = driver.session(AsyncSession.class, config.build());
        TransactionConfig transaction = TransactionConfig.builder().withTimeout(TIMEOUT).build();
        return session.runAsync(cypher, transaction)
            .thenCompose(cursor -> cursor.listAsync())
            .whenComplete((records, error) -> session.closeAsync())
            .toCompletableFuture();
    }

    private static Neo4jInfo.@Nullable Server server(List<Record> components) {
        return components.stream()
            .findFirst()
            .map(r -> new Neo4jInfo.Server(r.get("name").asString(), r.get("version").asString(""), r.get("edition").asString("")))
            .orElse(null);
    }

    private static long count(List<Record> records) {
        return records.isEmpty() ? 0 : records.getFirst().get("count").asLong();
    }

    /**
     * Removes the user info, query and fragment of a URI, which can carry credentials.
     *
     * @param uri the URI
     * @return the URI without user info, query or fragment
     */
    static String sanitize(@Nullable URI uri) {
        if (uri == null) {
            return "(not configured)";
        }
        if (uri.getHost() == null) {
            // java.net.URI falls back to a registry-based authority, with no user info, when it cannot parse the
            // host, for example for a password with an unencoded '@', so the raw value is checked instead
            String raw = uri.toString();
            return raw.contains("@") || raw.contains("?") ? "(hidden)" : raw;
        }
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            return "(hidden)";
        }
    }

    /**
     * Describes an error without its message, which can contain the server's response.
     *
     * @param error the error
     * @return the Neo4j status code, or the error type
     */
    static String describe(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof Neo4jException neo4j && neo4j.code() != null) {
            return neo4j.getClass().getSimpleName() + " (" + neo4j.code() + ")";
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
