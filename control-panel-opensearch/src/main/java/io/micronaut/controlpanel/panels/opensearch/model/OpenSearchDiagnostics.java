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
package io.micronaut.controlpanel.panels.opensearch.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;
import java.util.Locale;

/**
 * Body rendered by the OpenSearch control panel.
 */
@ReflectiveAccess
public record OpenSearchDiagnostics(String beanName,
                                    ConnectionContext connection,
                                    DiagnosticState state,
                                    ClusterHealth clusterHealth,
                                    List<IndexSummary> indices,
                                    String message,
                                    boolean partial,
                                    boolean indicesTruncated,
                                    boolean hasIndices,
                                    boolean available,
                                    String statusLabel,
                                    String statusBadgeClass) {

    public OpenSearchDiagnostics {
        indices = indices == null ? List.of() : List.copyOf(indices);
        hasIndices = !indices.isEmpty();
        available = state == DiagnosticState.AVAILABLE || state == DiagnosticState.RED || state == DiagnosticState.PARTIAL;
        statusLabel = statusLabel(state, clusterHealth);
        statusBadgeClass = statusBadgeClass(state, clusterHealth);
        message = message == null ? "" : message;
    }

    public static OpenSearchDiagnostics available(String beanName,
                                                  ConnectionContext connection,
                                                  DiagnosticState state,
                                                  ClusterHealth clusterHealth,
                                                  List<IndexSummary> indices,
                                                  String message,
                                                  boolean indicesTruncated) {
        boolean partial = message != null && !message.isBlank();
        DiagnosticState resolved = partial ? DiagnosticState.PARTIAL : state;
        return new OpenSearchDiagnostics(beanName, connection, resolved, clusterHealth, indices, message, partial, indicesTruncated, false, false, "", "");
    }

    public static OpenSearchDiagnostics failure(String beanName, ConnectionContext connection, DiagnosticState state, String message) {
        return new OpenSearchDiagnostics(beanName, connection, state, null, List.of(), message, false, false, false, false, "", "");
    }

    private static String statusLabel(DiagnosticState state, ClusterHealth health) {
        if (health != null && health.status() != null && !health.status().isBlank()) {
            return health.status().toUpperCase(Locale.ENGLISH);
        }
        return switch (state) {
            case AUTHENTICATION_FAILED -> "Authentication";
            case AUTHORIZATION_FAILED -> "Authorization";
            case UNAVAILABLE -> "Unavailable";
            case UNSUPPORTED -> "Unsupported";
            case PARTIAL -> "Partial";
            case NO_CLIENT -> "No client";
            default -> "Error";
        };
    }

    private static String statusBadgeClass(DiagnosticState state, ClusterHealth health) {
        String status = health == null ? "" : health.status();
        if ("green".equalsIgnoreCase(status)) {
            return "badge-success";
        }
        if ("yellow".equalsIgnoreCase(status) || state == DiagnosticState.PARTIAL) {
            return "badge-warning";
        }
        if ("red".equalsIgnoreCase(status)
            || state == DiagnosticState.AUTHENTICATION_FAILED
            || state == DiagnosticState.AUTHORIZATION_FAILED
            || state == DiagnosticState.UNAVAILABLE
            || state == DiagnosticState.ERROR) {
            return "badge-danger";
        }
        return "badge-secondary";
    }
}
