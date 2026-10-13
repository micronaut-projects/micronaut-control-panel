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
package io.micronaut.controlpanel.panels.testing;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the JSON of a continuous testing report into maps, lists, strings, numbers, booleans and nulls, without a JSON
 * mapper: the application need not have one.
 */
final class ReportJson {

    private static final int MAX_DEPTH = 64;

    private final String text;
    private int position;

    private ReportJson(String text) {
        this.text = text;
    }

    /**
     * Reads a JSON document.
     *
     * @param text The document
     * @return Its value: a {@link Map}, a {@link List}, a {@link String}, a {@link Number}, a {@link Boolean} or null
     * @throws IllegalArgumentException if it is not valid JSON
     */
    static @Nullable Object parse(String text) {
        ReportJson json = new ReportJson(text);
        Object value = json.value(0);
        json.whitespace();
        if (json.position != text.length()) {
            throw json.error("Unexpected content");
        }
        return value;
    }

    private @Nullable Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("Too deeply nested");
        }
        whitespace();
        if (position >= text.length()) {
            throw error("Unexpected end");
        }
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, @Nullable Object> object(int depth) {
        position++;
        Map<String, @Nullable Object> map = new LinkedHashMap<>();
        whitespace();
        if (peek('}')) {
            position++;
            return map;
        }
        while (true) {
            whitespace();
            if (!peek('"')) {
                throw error("Expected a name");
            }
            String name = string();
            whitespace();
            expect(':');
            map.put(name, value(depth + 1));
            whitespace();
            if (peek(',')) {
                position++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<@Nullable Object> array(int depth) {
        position++;
        List<@Nullable Object> list = new ArrayList<>();
        whitespace();
        if (peek(']')) {
            position++;
            return list;
        }
        while (true) {
            list.add(value(depth + 1));
            whitespace();
            if (peek(',')) {
                position++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        position++;
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (position >= text.length()) {
                break;
            }
            char escaped = text.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) {
                        throw error("Invalid escape");
                    }
                    try {
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("Invalid escape");
                    }
                    position += 4;
                }
                default -> throw error("Invalid escape");
            }
        }
        throw error("Unterminated string");
    }

    private Number number() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        String number = text.substring(start, position);
        if (number.isEmpty()) {
            throw error("Unexpected character");
        }
        try {
            if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) {
                return Long.parseLong(number);
            }
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw error("Invalid number");
        }
    }

    private @Nullable Object literal(String literal, @Nullable Object value) {
        if (!text.startsWith(literal, position)) {
            throw error("Unexpected character");
        }
        position += literal.length();
        return value;
    }

    private void whitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }
        position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + position);
    }
}
