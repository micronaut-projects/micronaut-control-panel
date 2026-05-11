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
package io.micronaut.controlpanel.panels.tracing;

import jakarta.inject.Singleton;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Redacts tracing configuration before it is exposed to templates.
 */
@Singleton
final class TracingRedactor {

    static final String REDACTED = "[redacted]";

    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "access_token",
            "api_key",
            "apikey",
            "authorization",
            "client_secret",
            "credential",
            "key",
            "password",
            "secret",
            "token"
    );

    String redact(String key, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (isSensitiveKey(key)) {
            return REDACTED;
        }
        if (looksLikeUrl(value)) {
            return redactUrl(value);
        }
        return value;
    }

    boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('_', '-');
        return normalized.contains("password")
                || normalized.contains("credential")
                || normalized.contains("certificate")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("api-key")
                || normalized.contains("apikey")
                || normalized.contains("authorization")
                || normalized.contains("headers")
                || normalized.contains("auth")
                || normalized.contains("bearer")
                || normalized.endsWith(".key")
                || normalized.endsWith("-key")
                || normalized.equals("key");
    }

    private String redactUrl(String value) {
        try {
            URI uri = new URI(value);
            String userInfo = uri.getRawUserInfo() == null ? null : REDACTED;
            String query = redactQuery(uri.getRawQuery());
            return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getRawPath(), query, uri.getRawFragment()).toString();
        } catch (URISyntaxException e) {
            return value.replaceFirst("(?i)(://)[^/?#@]+@", "$1" + REDACTED + "@");
        }
    }

    private String redactQuery(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        StringJoiner joiner = new StringJoiner("&");
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            if (SENSITIVE_QUERY_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                joiner.add(key + "=" + REDACTED);
            } else {
                joiner.add(part);
            }
        }
        return joiner.toString();
    }

    private static boolean looksLikeUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }
}
