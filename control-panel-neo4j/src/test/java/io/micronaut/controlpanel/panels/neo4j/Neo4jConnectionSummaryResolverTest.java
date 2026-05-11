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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jConnectionSummaryResolverTest {

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
