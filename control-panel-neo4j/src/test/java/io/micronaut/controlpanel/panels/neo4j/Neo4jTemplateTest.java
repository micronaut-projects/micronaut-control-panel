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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jTemplateTest {

    @Test
    void templatesDoNotUseTripleStashForDatabaseDerivedValues() throws IOException {
        assertEscaped("/views/neo4j/body.hbs");
        assertEscaped("/views/neo4j/detail.hbs");
    }

    @Test
    void detailTabsUseDashboardTabAttributes() throws IOException {
        String template = template("/views/neo4j/detail.hbs");

        assertFalse(template.contains("data-tab-target"));
        assertContains(template, "data-tabs-trigger=\"neo4j-connection\"");
        assertContains(template, "data-tabs-trigger=\"neo4j-schema\"");
        assertContains(template, "data-tabs-trigger=\"neo4j-diagnostics\"");
        assertContains(template, "data-tabs-panel=\"neo4j-connection\"");
        assertContains(template, "data-tabs-panel=\"neo4j-schema\"");
        assertContains(template, "data-tabs-panel=\"neo4j-diagnostics\"");
    }

    private static void assertEscaped(String path) throws IOException {
        String template = template(path);

        assertFalse(template.contains("{{{"));
        assertFalse(template.contains("&{"));
    }

    private static void assertContains(String template, String expected) {
        assertTrue(template.contains(expected), expected);
    }

    private static String template(String path) throws IOException {
        try (var in = Neo4jTemplateTest.class.getResourceAsStream(path)) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
