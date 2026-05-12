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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class MongoDbSanitizer {

    static final String REDACTED = "[redacted]";
    private static final Pattern CREDENTIALS = Pattern.compile("(mongodb(?:\\+srv)?://)([^/@]+)@");
    private static final List<String> SENSITIVE_NAME_PARTS = List.of(
        "auth",
        "credential",
        "password",
        "secret",
        "token",
        "key",
        "certificate"
    );

    String redactText(String value) {
        if (value == null) {
            return "";
        }
        return CREDENTIALS.matcher(value).replaceAll("$1" + REDACTED + "@");
    }

    Document redactDocument(Document document) {
        Document redacted = new Document();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            redacted.put(entry.getKey(), redactValue(entry.getKey(), entry.getValue()));
        }
        return redacted;
    }

    Object redactValue(String key, Object value) {
        if (sensitive(key)) {
            return REDACTED;
        }
        if (value instanceof Document document) {
            return redactDocument(document);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .map(item -> item instanceof Document document ? redactDocument(document) : item)
                .toList();
        }
        if (value instanceof String string) {
            return redactText(string);
        }
        return value;
    }

    String toJson(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Document document) {
            return redactDocument(document).toJson();
        }
        return String.valueOf(redactValue("", value));
    }

    private boolean sensitive(String key) {
        var lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_NAME_PARTS.stream().anyMatch(lower::contains);
    }
}
