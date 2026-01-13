/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.ColumnType;
import io.micronaut.controlpanel.panels.datasource.model.ForeignKey;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for generating Mermaid diagrams from datasource metadata.
 */
@Internal
public final class MermaidUtils {

    private MermaidUtils() {
    }

    /**
     * Generate a Mermaid ER diagram for the given tables metadata.
     * It includes entities with attributes (type, size, NOT NULL) and relationships based on foreign keys.
     *
     * @param tables Tables discovered by DataSourceService#getTables()
     * @return Mermaid ER diagram code
     */
    public static String generateMermaidER(List<Table> tables) {
        // Build table id and display names
        Map<String, String> tableIdByDisplay = new LinkedHashMap<>(); // display -> id
        Map<String, Table> tableByDisplay = new LinkedHashMap<>();

        for (Table t : tables) {
            String display = displayName(t.schema(), t.name());
            String id = sanitizeId(display);
            tableIdByDisplay.put(display, id);
            tableByDisplay.put(display, t);
        }

        // Collect unique columns (UK) and relationships from already discovered metadata (no DB calls)
        Map<String, Set<String>> uniqueColsByDisplay = new HashMap<>(); // display -> col names
        Set<String> relationshipLines = new LinkedHashSet<>();

        // Unique columns per table
        for (Table t : tables) {
            String display = displayName(t.schema(), t.name());
            uniqueColsByDisplay.put(display, t.uniqueColumns() == null ? Set.of() : new HashSet<>(t.uniqueColumns()));
        }

        // Relationships from foreign keys
        for (Table t : tables) {
            String fkDisplay = displayName(t.schema(), t.name());
            for (ForeignKey fk : t.foreignKeys() == null ? List.<ForeignKey>of() : t.foreignKeys()) {
                String pkDisplay = displayName(fk.pkSchema(), fk.pkTable());

                String pkId = tableIdByDisplay.get(pkDisplay);
                String fkId = tableIdByDisplay.get(fkDisplay);
                if (pkId == null || fkId == null) {
                    continue;
                }

                String rawLabel = fk.name() != null ? fk.name() : ("FK " + fk.fkColumn());
                String label = sanitizeRelationLabel(rawLabel);
                String line = "    " + pkId + " ||..o{ " + fkId + " : " + label + "\n";
                relationshipLines.add(line);
            }
        }

        // Build ER
        StringBuilder sb = new StringBuilder();
        sb.append("erDiagram\n");

        for (Map.Entry<String, Table> entry : tableByDisplay.entrySet()) {
            String display = entry.getKey();
            Table t = entry.getValue();
            String id = tableIdByDisplay.get(display);
            sb.append("    ").append(id).append(" {\n");
            Set<String> ucols = uniqueColsByDisplay.getOrDefault(display, Set.of());

            for (Column c : t.columns()) {
                String type = mermaidType(c.type());
                String typeWithSize = type;
                if (c.type() == ColumnType.TEXT && c.size() > 0) {
                    typeWithSize = type + "(" + c.size() + ")";
                } else if (c.type() == ColumnType.NUMERIC && c.size() > 0) {
                    typeWithSize = "numeric(" + c.size() + ")";
                }

                String attrName = sanitizeAttrName(c.name());
                boolean notNull = "NO".equalsIgnoreCase(c.nullable());

                List<String> keys = new ArrayList<>();
                if (c.isPrimaryKey()) {
                    keys.add("PK");
                }
                // mark UKs only when not PK to avoid duplicating PK and UK on same column
                if (!c.isPrimaryKey() && ucols.contains(c.name())) {
                    keys.add("UK");
                }
                if (c.isForeignKey()) {
                    keys.add("FK");
                }

                sb.append("        ")
                  .append(typeWithSize).append(" ")
                  .append(attrName);

                if (!keys.isEmpty()) {
                    sb.append(" ").append(String.join(", ", keys));
                }

                String comment = null;
                if (notNull) {
                    comment = "NOT NULL";
                }
                if (!attrName.equals(c.name())) {
                    comment = (comment == null ? "" : comment + "; ") + "original: " + c.name();
                }
                if (comment != null) {
                    sb.append(" ").append("\"").append(comment).append("\"");
                }

                sb.append("\n");
            }
            sb.append("    }\n");
        }

        if (tableByDisplay.isEmpty()) {
            sb.append("    EMPTY {\n");
            sb.append("        string note \"No tables detected\"\n");
            sb.append("    }\n");
        }

        for (String rel : relationshipLines) {
            sb.append(rel);
        }

        return sb.toString();
    }

    private static String sanitizeRelationLabel(String s) {
        if (s == null) {
            return "rel";
        }
        // Mermaid relation labels should avoid quotes/colons/newlines and weird symbols
        String sanitized = s.replaceAll("[:\"\\n\\r\\t]", " ");
        sanitized = sanitized.replaceAll("[^A-Za-z0-9 _\\-./()]", "_").trim();
        if (sanitized.isEmpty()) {
            return "rel";
        }
        return sanitized;
    }

    private static String mermaidType(ColumnType ct) {
        return switch (ct) {
            case TEXT, GENERIC -> "string";
            case NUMERIC -> "numeric";
            case DATE -> "date";
            case BLOB -> "blob";
            case BOOLEAN -> "boolean";
        };
    }

    private static String sanitizeId(String s) {
        // Mermaid identifiers: use letters, digits and underscore. Ensure starts with a letter.
        String base = (s == null ? "" : s).replaceAll("[^A-Za-z0-9_]", "_");
        // Collapse multiple underscores
        base = base.replaceAll("_+", "_");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "T_" + base;
        }
        return base;
    }

    private static String sanitizeAttrName(String s) {
        if (s == null || s.isBlank()) {
            return "col";
        }
        // Ensure no '*' remains in attribute names (older rendering used '*' for PK)
        String sanitized = s.replace("*", "").replaceAll("[^A-Za-z0-9_\\-\\[\\]()]", "_");
        if (!sanitized.isEmpty() && !Character.isLetter(sanitized.charAt(0))) {
            sanitized = "c_" + sanitized;
        }
        return sanitized;
    }

    private static String displayName(String schema, String table) {
        if (schema == null || schema.isBlank()) {
            return table;
        }
        return schema + "." + table;
    }
}
