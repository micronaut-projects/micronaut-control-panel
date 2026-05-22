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

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDbSchemaAnalyzerTest {

    @Test
    void schemaSamplingIsDisabledByDefault() {
        var analyzer = new MongoDbSchemaAnalyzer(new MongoDbControlPanelConfiguration(), new MongoDbSanitizer());

        var summary = analyzer.summarize(List.of(new Document("name", "Alice")));

        assertFalse(summary.enabled());
        assertTrue(summary.fields().isEmpty());
    }

    @Test
    void summarizesFieldTypesAndFrequencyWhenEnabled() {
        var configuration = new MongoDbControlPanelConfiguration();
        configuration.getSchema().setEnabled(true);
        var analyzer = new MongoDbSchemaAnalyzer(configuration, new MongoDbSanitizer());

        var summary = analyzer.summarize(List.of(
            new Document("name", "Alice").append("age", 42).append("address", new Document("city", "Madrid")),
            new Document("name", "Bob")
        ));

        assertTrue(summary.enabled());
        assertEquals(2, summary.sampledDocuments());
        assertTrue(summary.fields().stream().anyMatch(field -> field.path().equals("name") && field.frequency().equals("100%")));
        assertTrue(summary.fields().stream().anyMatch(field -> field.path().equals("address.city") && field.frequency().equals("50%")));
    }

    @Test
    void enforcesMaxFields() {
        var configuration = new MongoDbControlPanelConfiguration();
        configuration.getSchema().setEnabled(true);
        configuration.getSchema().setMaxFields(1);
        var analyzer = new MongoDbSchemaAnalyzer(configuration, new MongoDbSanitizer());

        var summary = analyzer.summarize(List.of(new Document("a", 1).append("b", 2)));

        assertEquals(1, summary.fields().size());
        assertTrue(summary.truncated());
    }

    @Test
    void arrayOccurrencesAreCountedOncePerDocumentForFrequency() {
        var configuration = new MongoDbControlPanelConfiguration();
        configuration.getSchema().setEnabled(true);
        var analyzer = new MongoDbSchemaAnalyzer(configuration, new MongoDbSanitizer());

        var summary = analyzer.summarize(List.of(
            new Document("tags", List.of("fiction", "classic", "award")),
            new Document("title", "Dune")
        ));

        var tags = summary.fields().stream()
            .filter(field -> field.path().equals("tags[]"))
            .findFirst()
            .orElseThrow();
        assertEquals(1, tags.count());
        assertEquals("50%", tags.frequency());
    }
}
