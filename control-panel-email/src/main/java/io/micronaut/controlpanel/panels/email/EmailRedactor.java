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
package io.micronaut.controlpanel.panels.email;

import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Redacts secret-like email diagnostics values before they reach models or responses.
 */
@Internal
@Singleton
public final class EmailRedactor {

    public static final String REDACTED = "[redacted]";

    private static final Pattern URI_USERINFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@:]+:[^\\s/@]+@");
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(bearer|basic)\\s+[^\\s,;]+");
    private static final Pattern AUTH_TOKEN = Pattern.compile("(?i)\\b(bearer|basic)\\s+[^\\s,;]+");

    boolean isSecretKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("authorization")
            || normalized.contains("api-key")
            || normalized.contains("api.secret")
            || normalized.contains("api-secret")
            || normalized.contains("api.token")
            || normalized.contains("api-token")
            || normalized.contains("access-key")
            || normalized.contains("secret-key")
            || normalized.contains("private-key")
            || normalized.endsWith(".key")
            || normalized.endsWith("-key")
            || normalized.endsWith("_key")
            || normalized.contains(".api-key");
    }

    String safeValue(String key, Object value) {
        if (value == null) {
            return "not configured";
        }
        if (isSecretKey(key)) {
            return "present (hidden)";
        }
        return redact(String.valueOf(value));
    }

    String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = URI_USERINFO.matcher(value).replaceAll("$1" + REDACTED + "@");
        redacted = AUTH_HEADER.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = AUTH_TOKEN.matcher(redacted).replaceAll("$1 " + REDACTED);
        return redactKeyValues(redacted);
    }

    private String redactKeyValues(String value) {
        StringBuilder redacted = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (!isKeyCharacter(current)) {
                redacted.append(current);
                index++;
                continue;
            }
            int keyStart = index;
            while (index < value.length() && isKeyCharacter(value.charAt(index))) {
                index++;
            }
            String key = value.substring(keyStart, index);
            if (isSecretMessageKey(key)) {
                int valueStart = valueStart(value, index);
                if (valueStart > index && valueStart < value.length() && !isValueDelimiter(value.charAt(valueStart))) {
                    redacted.append(key).append('=').append(REDACTED);
                    index = valueStart;
                    while (index < value.length() && !isValueDelimiter(value.charAt(index))) {
                        index++;
                    }
                    continue;
                }
            }
            redacted.append(key);
        }
        return redacted.toString();
    }

    private boolean isSecretMessageKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("authorization")
            || normalized.contains("key");
    }

    private int valueStart(String value, int separatorStart) {
        int index = separatorStart;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index < value.length() && (value.charAt(index) == ':' || value.charAt(index) == '=')) {
            index++;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }
        return index;
    }

    private boolean isKeyCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-' || character == '.';
    }

    private boolean isValueDelimiter(char character) {
        return Character.isWhitespace(character) || character == ',' || character == ';';
    }
}
