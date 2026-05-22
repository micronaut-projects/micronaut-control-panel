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

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.panels.neo4j.model.Neo4jConnectionInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jConnectionSummaryResolverTest {

    @Test
    void resolvesDefaultConnectionProperties() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "neo4j.uri", "neo4j://user:password@localhost:7687?token=abc",
            "neo4j.username", "neo4j",
            "neo4j.encrypted", "true",
            "neo4j.trust-strategy", "TRUST_CUSTOM_CA_SIGNED_CERTIFICATES; password=secret"
        ))) {
            Neo4jConnectionInfo info = new Neo4jConnectionSummaryResolver("default", context.getEnvironment()).resolve();

            assertEquals("default", info.beanName());
            assertEquals("neo4j://***@localhost:7687?token=***", info.uri());
            assertEquals("neo4j", info.username());
            assertEquals("true", info.encryption());
            assertEquals("TRUST_CUSTOM_CA_SIGNED_CERTIFICATES; password=***", info.trustStrategy());
            assertFalse(info.toString().contains("secret"));
        }
    }

    @Test
    void namedConnectionFallsBackToDefaultProperties() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "neo4j.uri", "bolt://localhost:7687",
            "neo4j.orders.username", "orders",
            "neo4j.encryption", "false",
            "neo4j.trustStrategy", "token=abc"
        ))) {
            Neo4jConnectionInfo info = new Neo4jConnectionSummaryResolver("orders", context.getEnvironment()).resolve();

            assertEquals("orders", info.beanName());
            assertEquals("bolt://localhost:7687", info.uri());
            assertEquals("orders", info.username());
            assertEquals("false", info.encryption());
            assertEquals("token=***", info.trustStrategy());
        }
    }

    @Test
    void returnsCustomDriverSummaryWhenNoPropertiesAreAvailable() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            Neo4jConnectionInfo info = new Neo4jConnectionSummaryResolver("custom", context.getEnvironment()).resolve();

            assertEquals("custom", info.beanName());
            assertEquals("custom Driver bean", info.uri());
            assertEquals("", info.username());
            assertEquals("", info.encryption());
            assertEquals("", info.trustStrategy());
        }
    }

    @Test
    void masksUriUserInfoAndSecretQueryParameters() {
        String masked = Neo4jConnectionSummaryResolver.maskUri(
            "neo4j://user:password@localhost:7687?token=abc&database=movies&apiKey=secret"
        );

        assertEquals("neo4j://***@localhost:7687?token=***&database=movies&apiKey=***", masked);
        assertFalse(masked.contains("password"));
        assertFalse(masked.contains("abc"));
        assertFalse(masked.contains("secret"));
    }

    @Test
    void masksBlankUriAsEmptyValue() {
        assertEquals("", Neo4jConnectionSummaryResolver.maskUri(null));
        assertEquals("", Neo4jConnectionSummaryResolver.maskUri(" "));
    }

    @Test
    void masksUserInfoWhenUriParsingFallsBack() {
        String masked = Neo4jConnectionSummaryResolver.maskUri("user:password@localhost:7687");

        assertEquals("***@localhost:7687", masked);
        assertFalse(masked.contains("password"));
    }

    @Test
    void masksSecretAssignmentsInFreeFormValues() {
        String masked = Neo4jConnectionSummaryResolver.maskSecretValue("password=hunter2, token:abc, user=neo4j");

        assertEquals("password=***, token:***, user=neo4j", masked);
        assertFalse(masked.contains("hunter2"));
        assertFalse(masked.contains("abc"));
    }

    @Test
    void detectsSecretNames() {
        assertTrue(Neo4jConnectionSummaryResolver.isSecretName("clientToken"));
        assertTrue(Neo4jConnectionSummaryResolver.isSecretName("private_key"));
        assertFalse(Neo4jConnectionSummaryResolver.isSecretName("database"));
    }
}
