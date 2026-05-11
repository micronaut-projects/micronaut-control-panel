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
    private static final Pattern KEY_VALUE = Pattern.compile("(?i)\\b(password|secret|token|api[-_]?key|api[-_]?secret|api[-_]?token|access[-_]?key|secret[-_]?key|private[-_]?key|credential|authorization)\\b\\s*[:= ]\\s*[^\\s,;]+");

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
        redacted = KEY_VALUE.matcher(redacted).replaceAll("$1=" + REDACTED);
        return redacted;
    }
}
