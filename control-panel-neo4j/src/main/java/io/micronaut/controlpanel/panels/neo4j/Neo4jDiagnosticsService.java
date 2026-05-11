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
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jBody;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jConnectionInfo;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jDiagnostic;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jServerInfo;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jStatus;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Read-only diagnostics for a Neo4j driver bean.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
public class Neo4jDiagnosticsService {

    private static final Logger LOG = LoggerFactory.getLogger(Neo4jDiagnosticsService.class);
    private static final String LABELS_QUERY = "CALL db.labels() YIELD label RETURN label ORDER BY label LIMIT $limit";
    private static final String RELATIONSHIP_TYPES_QUERY = "CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType ORDER BY relationshipType LIMIT $limit";
    private static final String PROPERTY_KEYS_QUERY = "CALL db.propertyKeys() YIELD propertyKey RETURN propertyKey ORDER BY propertyKey LIMIT $limit";
    private static final SessionConfig READ_SESSION = SessionConfig.builder()
        .withDefaultAccessMode(AccessMode.READ)
        .build();

    private final String beanName;
    private final BeanContext beanContext;
    private final Neo4jPanelConfiguration configuration;
    private final Neo4jConnectionSummaryResolver connectionSummaryResolver;

    /**
     * Constructor.
     *
     * @param beanName Neo4j driver bean name
     * @param beanContext bean context used to resolve the driver lazily
     * @param configuration panel configuration
     * @param connectionSummaryResolver connection summary resolver
     */
    public Neo4jDiagnosticsService(String beanName,
                                   BeanContext beanContext,
                                   Neo4jPanelConfiguration configuration,
                                   Neo4jConnectionSummaryResolver connectionSummaryResolver) {
        this.beanName = beanName;
        this.beanContext = beanContext;
        this.configuration = configuration;
        this.connectionSummaryResolver = connectionSummaryResolver;
    }

    /**
     * Computes the current diagnostics body. Failures are represented as non-fatal diagnostics.
     *
     * @return Neo4j diagnostics body
     */
    public Neo4jBody getBody() {
        Neo4jConnectionInfo connectionInfo = connectionSummaryResolver.resolve();
        List<Neo4jDiagnostic> diagnostics = new ArrayList<>();
        Neo4jServerInfo serverInfo = Neo4jServerInfo.EMPTY;
        Neo4jStatus status = Neo4jStatus.connectedStatus();
        List<String> labels = List.of();
        List<String> relationshipTypes = List.of();
        List<String> propertyKeys = List.of();

        try {
            Driver driver = driver();
            driver.verifyConnectivity();
            try (Session session = driver.session(READ_SESSION)) {
                serverInfo = serverInfo(session, diagnostics);
                labels = metadata(session, LABELS_QUERY, "label", configuration.getMaxLabels(), "Labels", diagnostics);
                relationshipTypes = metadata(session, RELATIONSHIP_TYPES_QUERY, "relationshipType", configuration.getMaxRelationshipTypes(), "Relationship types", diagnostics);
                propertyKeys = metadata(session, PROPERTY_KEYS_QUERY, "propertyKey", configuration.getMaxPropertyKeys(), "Property keys", diagnostics);
            }
        } catch (RuntimeException e) {
            status = Neo4jStatus.unavailable();
            diagnostics.add(toDiagnostic("Connectivity", e));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Neo4j connectivity probe failed with {}", e.getClass().getSimpleName());
            }
        }
        return new Neo4jBody(connectionInfo, status, serverInfo, labels, relationshipTypes, propertyKeys, List.copyOf(diagnostics), false);
    }

    private Driver driver() {
        if ("default".equals(beanName)) {
            return beanContext.getBean(Driver.class);
        }
        return beanContext.getBean(Driver.class, Qualifiers.byName(beanName));
    }

    private static Neo4jServerInfo serverInfo(Session session, List<Neo4jDiagnostic> diagnostics) {
        try {
            ResultSummary summary = session.run("RETURN 1 AS ok").consume();
            ServerInfo server = summary.server();
            if (server == null) {
                return Neo4jServerInfo.EMPTY;
            }
            return new Neo4jServerInfo(
                valueOrEmpty(server.agent()),
                valueOrEmpty(server.address()),
                valueOrEmpty(server.protocolVersion())
            );
        } catch (RuntimeException e) {
            diagnostics.add(toDiagnostic("Server info", e));
            return Neo4jServerInfo.EMPTY;
        }
    }

    private static List<String> metadata(Session session,
                                         String query,
                                         String column,
                                         int limit,
                                         String area,
                                         List<Neo4jDiagnostic> diagnostics) {
        if (limit == 0) {
            return List.of();
        }
        try {
            Result result = session.run(query, Map.of("limit", limit));
            return result.list(toStringValue(column));
        } catch (RuntimeException e) {
            diagnostics.add(toDiagnostic(area, e));
            return List.of();
        }
    }

    private static Function<Record, String> toStringValue(String column) {
        return record -> record.get(column).asString("");
    }

    static Neo4jDiagnostic toDiagnostic(String area, RuntimeException exception) {
        boolean permission = isPermissionException(exception);
        return new Neo4jDiagnostic(area, safeMessage(exception, permission), permission);
    }

    static boolean isPermissionException(RuntimeException exception) {
        if (exception instanceof ClientException clientException) {
            String code = valueOrEmpty(clientException.code()).toLowerCase(Locale.ROOT);
            String message = valueOrEmpty(clientException.getMessage()).toLowerCase(Locale.ROOT);
            return code.contains("security.authorization")
                || code.contains("security.forbidden")
                || message.contains("permission")
                || message.contains("unauthorized")
                || message.contains("forbidden");
        }
        return false;
    }

    static String safeMessage(RuntimeException exception, boolean permission) {
        if (permission) {
            return "Metadata is hidden because the current Neo4j user does not have permission to read it.";
        }
        if (exception instanceof AuthenticationException) {
            return "Authentication failed while connecting to Neo4j.";
        }
        if (exception instanceof ServiceUnavailableException) {
            return "Neo4j is unavailable from this application.";
        }
        if (exception instanceof Neo4jException neo4jException) {
            String code = valueOrEmpty(neo4jException.code());
            if (!code.isBlank()) {
                return "Neo4j returned " + code + ".";
            }
        }
        return "Neo4j probe failed with " + exception.getClass().getSimpleName() + ".";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : Neo4jConnectionSummaryResolver.maskSecretValue(value);
    }
}
