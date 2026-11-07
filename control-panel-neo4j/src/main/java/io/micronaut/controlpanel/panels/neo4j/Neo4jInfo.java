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

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Neo4j server of the application. Internal to the Neo4j panel views.
 *
 * @param uri the configured URI, without user info or query string
 * @param authentication whether credentials are configured: {@code configured} or {@code none}, never the credentials
 * @param driverOpen whether the application has created the driver
 * @param server the server version and edition, or {@code null} if they were not requested or could not be read
 * @param databases the databases, sorted by name
 * @param graph the size of the default database, or {@code null} if it was not requested or could not be read
 * @param error why the server could not be read, without the server's message, or {@code null}
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record Neo4jInfo(
    String uri,
    String authentication,
    boolean driverOpen,
    @Nullable Server server,
    List<Database> databases,
    @Nullable Graph graph,
    @Nullable String error
) {

    /**
     * The server version and edition.
     *
     * @param name the component name, such as {@code Neo4j Kernel}
     * @param version the version
     * @param edition the edition, such as {@code community}
     */
    @ReflectiveAccess
    public record Server(String name, String version, String edition) {
    }

    /**
     * A database.
     *
     * @param name the database name
     * @param status the current status, such as {@code online}
     * @param isDefault whether it is the default database
     */
    @ReflectiveAccess
    public record Database(String name, String status, boolean isDefault) {

        /**
         * @return the CSS class of the status badge
         */
        public String badgeClass() {
            return "online".equals(status) ? "badge-secondary" : "badge-destructive";
        }
    }

    /**
     * The size of the default database.
     *
     * @param nodes the number of nodes
     * @param relationships the number of relationships
     * @param labels the node labels, sorted, capped at {@value Neo4jInspector#MAX_LABELS}
     * @param relationshipTypes the relationship types, sorted, capped at {@value Neo4jInspector#MAX_LABELS}
     */
    @ReflectiveAccess
    public record Graph(long nodes, long relationships, List<String> labels, List<String> relationshipTypes) {
    }
}
