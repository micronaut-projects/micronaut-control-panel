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
package io.micronaut.controlpanel.panels.openapi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OpenApiSupport {
    static final String OPENAPI_VIEWS_SPEC = "micronaut.openapi.views.spec";

    // canonical viewer ids
    static final String SWAGGER_UI = "swagger-ui";
    static final String REDOC = "redoc";
    static final String OPENAPI_EXPLORER = "openapi-explorer";
    static final String SCALAR = "scalar";
    static final String RAPIDOC = "rapidoc";

    private static final Map<String, String> DEFAULT_VIEWER_BASES = defaultViewerBases();

    private OpenApiSupport() { }

    private static Map<String, String> defaultViewerBases() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(SWAGGER_UI, "/swagger-ui");
        m.put(REDOC, "/redoc");
        m.put(OPENAPI_EXPLORER, "/openapi-explorer");
        m.put(SCALAR, "/scalar");
        m.put(RAPIDOC, "/rapidoc");
        return Map.copyOf(m);
    }

    static String defaultBaseFor(String viewer) {
        return DEFAULT_VIEWER_BASES.getOrDefault(viewer, "/" + viewer);
    }

    static List<String> parseEnabledViewers(String spec) {
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (String part : spec.split(",")) {
            String e = part.trim();
            if (e.isEmpty()) {
                continue;
            }
            int idx = e.indexOf('=');
            String key = idx >= 0 ? e.substring(0, idx).trim() : e;
            String value = idx >= 0 ? e.substring(idx + 1).trim() : "";
            String v = value.toLowerCase(Locale.ROOT);
            boolean isTrue = "true".equals(v);
            // accept endings like "swagger-ui.enabled"
            for (String viewer : DEFAULT_VIEWER_BASES.keySet()) {
                if (key.endsWith(viewer + ".enabled")) {
                    enabled.put(viewer, isTrue || enabled.getOrDefault(viewer, false));
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (var e : enabled.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                out.add(e.getKey());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Infer canonical viewer id from static-resources name.
     * Accepts variations like "rapi-doc", "swaggerui", "swagger", etc.
     */
    static String inferViewerFromResourceName(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace('_', '-');
        // normalize common variants
        n = n.replace("swaggerui", "swagger-ui");
        n = n.replace("swagger", "swagger-ui");
        n = n.replace("rapi-doc", "rapidoc").replace("rapi_doc", "rapidoc");
        n = n.replace("openapi-explorer", "openapi-explorer").replace("openapi_explorer", "openapi-explorer");
        n = n.replace("scalar", "scalar");
        n = n.replace("redocly", "redoc");
        if (n.contains("swagger-ui")) {
            return SWAGGER_UI;
        }
        if (n.contains("rapidoc")) {
            return RAPIDOC;
        }
        if (n.contains("redoc")) {
            return REDOC;
        }
        if (n.contains("openapi-explorer")) {
            return OPENAPI_EXPLORER;
        }
        if (n.contains("scalar")) {
            return SCALAR;
        }
        return null;
    }
}
