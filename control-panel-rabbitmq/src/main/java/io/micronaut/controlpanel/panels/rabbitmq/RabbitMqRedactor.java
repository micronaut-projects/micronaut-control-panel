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
package io.micronaut.controlpanel.panels.rabbitmq;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Redacts credential-like RabbitMQ values before rendering.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@Internal
final class RabbitMqRedactor {

    static final String REDACTED = "redacted";

    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|passwd|pwd|secret|token|credential|key).*");

    private RabbitMqRedactor() {
    }

    static String redact(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "not configured";
        }
        String trimmed = value.trim();
        String redactedUri = redactUri(trimmed);
        if (redactedUri != null) {
            return redactedUri;
        }
        if (looksSensitiveKey(trimmed)) {
            return REDACTED;
        }
        return trimmed;
    }

    static String redactKeyValue(String key, @Nullable Object value) {
        if (SECRET_KEY.matcher(key).matches()) {
            return REDACTED;
        }
        return redact(value == null ? null : String.valueOf(value));
    }

    static String redactCredential(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "not configured";
        }
        return REDACTED;
    }

    private static boolean looksSensitiveKey(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("password=")
            || lower.startsWith("passwd=")
            || lower.startsWith("token=")
            || lower.startsWith("secret=");
    }

    private static @Nullable String redactUri(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getUserInfo() == null) {
                return null;
            }
            int userInfoStart = value.indexOf("://") + 3;
            int userInfoEnd = value.indexOf('@', userInfoStart);
            return value.substring(0, userInfoStart) + REDACTED + value.substring(userInfoEnd);
        } catch (Exception e) {
            return null;
        }
    }
}
