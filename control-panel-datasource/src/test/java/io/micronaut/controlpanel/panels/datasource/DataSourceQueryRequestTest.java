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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the datasource query route honours its documented read-only contract and only accepts
 * JSON request bodies.
 */
class DataSourceQueryRequestTest {

    private static final String QUERY_PATH = "/control-panel/datasource-control-panel-controller/default/query";

    private static final String UI_HEADER = "X-Requested-With";
    private static final String UI_VALUE = "micronaut-control-panel";

    @Test
    void formEncodedBodyIsRejected() {
        try (EmbeddedServer server = server("formrejected");
             HttpClient client = client(server)) {
            HttpClientResponseException ex = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    HttpRequest.POST(QUERY_PATH, "sql=SELECT 1&start=0&length=10&draw=1")
                        .header(UI_HEADER, UI_VALUE)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            );
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
        }
    }

    @Test
    void ddlStatementIsNotExecutedByDefault() throws Exception {
        try (EmbeddedServer server = server("ddldefault");
             HttpClient client = client(server)) {
            try {
                client.toBlocking().exchange(
                    HttpRequest.POST(QUERY_PATH, Map.of(
                        "sql", "CREATE TABLE CP_CREATED(ID INT)",
                        "start", 0, "length", 10, "draw", 1))
                        .header(UI_HEADER, UI_VALUE)
                        .contentType(MediaType.APPLICATION_JSON_TYPE));
            } catch (HttpClientResponseException expected) {
                // A read-only route rejects the write; status is asserted below via table absence.
            }
            assertFalse(tableExists(server, "CP_CREATED"),
                "A write statement must not execute against the datasource by default");
        }
    }

    @Test
    void ddlStatementExecutesWhenWritesAllowed() throws Exception {
        try (EmbeddedServer server = serverAllowingWrites("ddlallowed");
             HttpClient client = client(server)) {
            client.toBlocking().exchange(
                HttpRequest.POST(QUERY_PATH, Map.of(
                    "sql", "CREATE TABLE CP_WRITABLE(ID INT)",
                    "start", 0, "length", 10, "draw", 1))
                    .header(UI_HEADER, UI_VALUE)
                    .contentType(MediaType.APPLICATION_JSON_TYPE));
            org.junit.jupiter.api.Assertions.assertTrue(tableExists(server, "CP_WRITABLE"));
        }
    }

    @Test
    void selectStatementStillWorks() {
        try (EmbeddedServer server = server("selectok");
             HttpClient client = client(server)) {
            HttpStatus status = client.toBlocking().exchange(
                HttpRequest.POST(QUERY_PATH, Map.of(
                    "sql", "SELECT 1", "start", 0, "length", 10, "draw", 1))
                    .header(UI_HEADER, UI_VALUE)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)).getStatus();
            assertEquals(HttpStatus.OK, status);
        }
    }

    @Test
    void cteWithKeywordInStringLiteralIsAccepted() {
        try (EmbeddedServer server = server("cteliteral");
             HttpClient client = client(server)) {
            HttpStatus status = client.toBlocking().exchange(
                HttpRequest.POST(QUERY_PATH, Map.of(
                    "sql", "WITH x AS (SELECT 'delete' AS action) SELECT * FROM x",
                    "start", 0, "length", 10, "draw", 1))
                    .header(UI_HEADER, UI_VALUE)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)).getStatus();
            assertEquals(HttpStatus.OK, status);
        }
    }

    @Test
    void multilineSelectIsAccepted() {
        try (EmbeddedServer server = server("multiline");
             HttpClient client = client(server)) {
            HttpStatus status = client.toBlocking().exchange(
                HttpRequest.POST(QUERY_PATH, Map.of(
                    "sql", "SELECT\n1", "start", 0, "length", 10, "draw", 1))
                    .header(UI_HEADER, UI_VALUE)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)).getStatus();
            assertEquals(HttpStatus.OK, status);
        }
    }

    @Test
    void crossSiteRequestWithoutHeaderIsRejected() {
        try (EmbeddedServer server = server("csrf");
             HttpClient client = client(server)) {
            HttpClientResponseException ex = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    HttpRequest.POST(QUERY_PATH, Map.of(
                        "sql", "SELECT 1", "start", 0, "length", 10, "draw", 1))
                        .contentType(MediaType.APPLICATION_JSON_TYPE))
            );
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }

    @Test
    void crossSiteFetchSiteIsRejected() {
        try (EmbeddedServer server = server("csrffetch");
             HttpClient client = client(server)) {
            HttpClientResponseException ex = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    HttpRequest.POST(QUERY_PATH, Map.of(
                        "sql", "SELECT 1", "start", 0, "length", 10, "draw", 1))
                        .header(UI_HEADER, UI_VALUE)
                        .header("Sec-Fetch-Site", "cross-site")
                        .contentType(MediaType.APPLICATION_JSON_TYPE))
            );
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }

    private static EmbeddedServer server(String database) {
        return ApplicationContext.run(EmbeddedServer.class, baseConfig(database));
    }

    private static EmbeddedServer serverAllowingWrites(String database) {
        var config = new java.util.HashMap<>(baseConfig(database));
        config.put("micronaut.control-panel.datasource.allow-writes", true);
        return ApplicationContext.run(EmbeddedServer.class, config);
    }

    private static Map<String, Object> baseConfig(String database) {
        return Map.of(
            "datasources.default.url", "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "datasources.default.password", ""
        );
    }

    private static HttpClient client(EmbeddedServer server) {
        return server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    private static boolean tableExists(EmbeddedServer server, String table) throws SQLException {
        DataSource dataSource = server.getApplicationContext().getBean(DataSource.class);
        try (var connection = dataSource.getConnection();
             var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }
}
