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

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.Column;
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.ColumnType;
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.Table;
import io.micronaut.controlpanel.panels.datasource.DataSourceControlPanel.ForeignKey;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for a specific {@link DataSource}, providing metadata about tables, columns, keys, etc.
 */
@EachBean(DataSource.class)
public class DataSourceService {

    private final DataSource dataSource;

    public DataSourceService(@Parameter DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    /**
     * Retrieves a list of tables from the database, including schema, columns, primary keys, and foreign keys.
     *
     * @return List of {@link Table} metadata objects
     */
    public final List<Table> getTables() {
        List<Table> tables = new ArrayList<>();
        try (var connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            String defaultSchema = connection.getSchema();
            DatabaseMetaData dbMetaData = connection.getMetaData();
            try (ResultSet tablesRs = dbMetaData.getTables(catalog, defaultSchema, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    String schema = tablesRs.getString("TABLE_SCHEM");
                    if (schema == null) {
                        schema = defaultSchema;
                    }
                    String tableName = tablesRs.getString("TABLE_NAME");

                    // Primary keys
                    List<String> primaryKeysList = new ArrayList<>();
                    try (ResultSet pkRs = dbMetaData.getPrimaryKeys(catalog, schema, tableName)) {
                        while (pkRs.next()) {
                            primaryKeysList.add(pkRs.getString("COLUMN_NAME"));
                        }
                    } catch (SQLException ignore) {
                    }

                    // Foreign keys (full info)
                    Set<String> foreignKeyColumns = new HashSet<>();
                    List<ForeignKey> foreignKeys = new ArrayList<>();
                    try (ResultSet fkRs = dbMetaData.getImportedKeys(catalog, schema, tableName)) {
                        while (fkRs.next()) {
                            String fkName = fkRs.getString("FK_NAME");
                            String fkColumn = fkRs.getString("FKCOLUMN_NAME");
                            String pkSchema = valueOrDefault(fkRs.getString("PKTABLE_SCHEM"), defaultSchema);
                            String pkTable = fkRs.getString("PKTABLE_NAME");
                            String pkColumn = fkRs.getString("PKCOLUMN_NAME");
                            foreignKeyColumns.add(fkColumn);
                            foreignKeys.add(new ForeignKey(fkName, fkColumn, pkSchema, pkTable, pkColumn));
                        }
                    } catch (SQLException ignore) {
                    }

                    // Unique columns (unique indexes/constraints)
                    Set<String> uniqueCols = new LinkedHashSet<>();
                    try (ResultSet idx = dbMetaData.getIndexInfo(catalog, schema, tableName, true, false)) {
                        while (idx.next()) {
                            boolean nonUnique = idx.getBoolean("NON_UNIQUE");
                            String col = idx.getString("COLUMN_NAME");
                            // Some drivers return null rows for table-level index metadata
                            if (!nonUnique && col != null) {
                                uniqueCols.add(col);
                            }
                        }
                    } catch (SQLException ignore) {
                    }

                    // Columns
                    List<Column> columnsList = new ArrayList<>();
                    try (ResultSet colsRs = dbMetaData.getColumns(catalog, schema, tableName, "%")) {
                        while (colsRs.next()) {
                            String columnName = colsRs.getString("COLUMN_NAME");
                            String columnTypeStr = colsRs.getString("TYPE_NAME");
                            int columnSize = colsRs.getInt("COLUMN_SIZE");
                            String nullable = colsRs.getString("IS_NULLABLE");
                            int dataTypeInt = colsRs.getInt("DATA_TYPE");
                            boolean isBinary = dataTypeInt == Types.BINARY ||
                                dataTypeInt == Types.VARBINARY ||
                                dataTypeInt == Types.LONGVARBINARY ||
                                dataTypeInt == Types.BLOB;
                            ColumnType columnType = mapToColumnType(dataTypeInt, columnTypeStr);
                            boolean isPrimaryKey = primaryKeysList.contains(columnName);
                            boolean isForeignKey = foreignKeyColumns.contains(columnName);
                            columnsList.add(new Column(columnName, columnType, columnSize, nullable, isBinary, isPrimaryKey, isForeignKey));
                        }
                    } catch (SQLException ignore) {
                    }

                    tables.add(new Table(schema, tableName, columnsList, uniqueCols, foreignKeys));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables;
    }

    private ColumnType mapToColumnType(int dataType, String typeName) {
        return switch (dataType) {
            case Types.BIT, Types.BOOLEAN -> ColumnType.BOOLEAN;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> ColumnType.NUMERIC;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                 Types.CLOB, Types.NCLOB -> ColumnType.TEXT;
            case Types.DATE, Types.TIME, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ColumnType.DATE;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> ColumnType.BLOB;
            default -> ColumnType.GENERIC;
        };
    }

    /**
     * Generate a Mermaid ER diagram for the current datasource based on the provided tables metadata.
     * It includes entities with attributes (type, size, NOT NULL) and relationships based on foreign keys.
     *
     * @param tables Tables discovered by {@link #getTables()}
     * @return Mermaid ER diagram code
     */
    public String generateMermaidER(List<Table> tables) {
        // Build table id and display names
        Map<String, String> tableIdByDisplay = new HashMap<>(); // display -> id
        Map<String, Table> tableByDisplay = new HashMap<>();

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

                boolean fkNullable = true;
                Table fkTableObj = tableByDisplay.get(fkDisplay);
                if (fkTableObj != null) {
                    for (Column c : fkTableObj.columns()) {
                        if (c.name().equalsIgnoreCase(fk.fkColumn())) {
                            fkNullable = !"NO".equalsIgnoreCase(c.nullable());
                            break;
                        }
                    }
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

                java.util.List<String> keys = new java.util.ArrayList<>();
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
            case TEXT -> "string";
            case NUMERIC -> "numeric";
            case DATE -> "date";
            case BLOB -> "blob";
            case BOOLEAN -> "boolean";
            case GENERIC -> "string";
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
        String sanitized = s.replace("*", "").replaceAll("[^A-Za-z0-9_\\-\\[\\]\\(\\)]", "_");
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

    private static String valueOrDefault(String v, String def) {
        return v == null ? def : v;
    }
}
