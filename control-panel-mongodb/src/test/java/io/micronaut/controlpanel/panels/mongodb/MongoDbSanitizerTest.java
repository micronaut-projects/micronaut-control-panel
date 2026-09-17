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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDbSanitizerTest {

    private final MongoDbSanitizer sanitizer = new MongoDbSanitizer();

    @Test
    void redactsCredentialsInConnectionStrings() {
        String redacted = sanitizer.redactText("mongodb://user:secret@localhost:27017/test?authSource=admin");

        assertEquals("mongodb://[redacted]@localhost:27017/test?authSource=admin", redacted);
    }

    @Test
    void redactsSensitiveDocumentValues() {
        var document = new Document("username", "user")
            .append("password", "secret")
            .append("nested", new Document("apiToken", "token-value"));

        var redacted = sanitizer.redactDocument(document);

        assertEquals("user", redacted.get("username"));
        assertEquals(MongoDbSanitizer.REDACTED, redacted.get("password"));
        assertEquals(MongoDbSanitizer.REDACTED, ((Document) redacted.get("nested")).get("apiToken"));
    }

    @Test
    void jsonOutputDoesNotLeakSecrets() {
        String json = sanitizer.toJson(new Document("sslKeyPassword", "secret").append("host", "localhost"));

        assertTrue(json.contains("localhost"));
        assertFalse(json.contains("secret"));
    }
}
