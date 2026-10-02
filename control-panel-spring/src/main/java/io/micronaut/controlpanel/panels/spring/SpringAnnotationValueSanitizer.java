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
package io.micronaut.controlpanel.panels.spring;

import io.micronaut.core.annotation.Internal;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Sanitizes optional annotation value summaries.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Internal
final class SpringAnnotationValueSanitizer {

    static final String HIDDEN = "hidden by configuration";
    static final String REDACTED = "redacted";

    private static final Collection<String> SENSITIVE_TERMS = List.of(
        "password",
        "secret",
        "token",
        "credential",
        "key",
        "certificate",
        "tenant",
        "user",
        "account"
    );

    String summarize(String name, Object value, boolean showValues) {
        if (!showValues) {
            return HIDDEN;
        }
        if (isSensitive(name) || isSensitive(String.valueOf(value))) {
            return REDACTED;
        }
        String text = String.valueOf(value);
        return text.length() > 120 ? text.substring(0, 117) + "..." : text;
    }

    boolean isSensitive(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return SENSITIVE_TERMS.stream().anyMatch(lower::contains);
    }
}
