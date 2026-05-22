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
package io.micronaut.controlpanel.panels.validation;

import io.micronaut.core.annotation.AnnotationClassValue;
import jakarta.inject.Singleton;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts annotation members into safe display strings.
 *
 * @since 2.0.0
 */
@Singleton
final class ValidationAttributeSanitizer {

    private static final int MAX_STRING_LENGTH = 120;
    private static final int MAX_ARRAY_ITEMS = 8;
    private static final List<String> SECRET_TOKENS = List.of("password", "secret", "token", "key", "credential");

    List<ValidationDiagnostics.AttributeRow> sanitize(Map<CharSequence, Object> values, ValidationConfiguration.AttributeMode mode) {
        if (mode == ValidationConfiguration.AttributeMode.NONE || values.isEmpty()) {
            return List.of();
        }
        List<ValidationDiagnostics.AttributeRow> rows = new ArrayList<>();
        values.entrySet().stream()
            .filter(e -> !isStandardMember(e.getKey().toString()))
            .sorted(Comparator.comparing(e -> e.getKey().toString()))
            .forEach(e -> rows.add(sanitize(e.getKey().toString(), e.getValue(), mode)));
        return List.copyOf(rows);
    }

    private static ValidationDiagnostics.AttributeRow sanitize(String name, Object value, ValidationConfiguration.AttributeMode mode) {
        boolean redacted = mode == ValidationConfiguration.AttributeMode.SAFE && isSecretLike(name);
        if (redacted) {
            return new ValidationDiagnostics.AttributeRow(name, "[redacted]", true);
        }
        String display = toDisplayValue(value, mode);
        return new ValidationDiagnostics.AttributeRow(name, display, mode == ValidationConfiguration.AttributeMode.SAFE && display.endsWith("[redacted]"));
    }

    private static String toDisplayValue(Object value, ValidationConfiguration.AttributeMode mode) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Class<?> type) {
            return type.getName();
        }
        if (value instanceof AnnotationClassValue<?> classValue) {
            return classValue.getName();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof CharSequence sequence) {
            return formatString(sequence, mode);
        }
        if (value.getClass().isArray()) {
            return formatArray(value, mode);
        }
        if (value instanceof Iterable<?> iterable) {
            return formatIterable(iterable, mode);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return mode == ValidationConfiguration.AttributeMode.SAFE ? value.getClass().getName() + " [redacted]" : value.toString();
    }

    private static String formatString(CharSequence sequence, ValidationConfiguration.AttributeMode mode) {
        String s = sequence.toString();
        if (mode == ValidationConfiguration.AttributeMode.SAFE && s.length() > MAX_STRING_LENGTH) {
            return s.substring(0, MAX_STRING_LENGTH) + "... [truncated]";
        }
        return s;
    }

    private static String formatArray(Object value, ValidationConfiguration.AttributeMode mode) {
        int length = Array.getLength(value);
        int limit = displayLimit(length, mode);
        List<String> parts = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            parts.add(toDisplayValue(Array.get(value, i), mode));
        }
        String suffix = length > limit ? ", ... (" + length + " total)" : "";
        return "[" + String.join(", ", parts) + suffix + "]";
    }

    private static String formatIterable(Iterable<?> iterable, ValidationConfiguration.AttributeMode mode) {
        List<String> parts = new ArrayList<>();
        int count = 0;
        for (Object item : iterable) {
            if (mode == ValidationConfiguration.AttributeMode.SAFE && count >= MAX_ARRAY_ITEMS) {
                parts.add("...");
                break;
            }
            parts.add(toDisplayValue(item, mode));
            count++;
        }
        return "[" + String.join(", ", parts) + "]";
    }

    private static int displayLimit(int length, ValidationConfiguration.AttributeMode mode) {
        return mode == ValidationConfiguration.AttributeMode.SAFE ? Math.min(length, MAX_ARRAY_ITEMS) : length;
    }

    private static boolean isStandardMember(String name) {
        return "message".equals(name) || "groups".equals(name) || "payload".equals(name);
    }

    private static boolean isSecretLike(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return SECRET_TOKENS.stream().anyMatch(normalized::contains);
    }
}
