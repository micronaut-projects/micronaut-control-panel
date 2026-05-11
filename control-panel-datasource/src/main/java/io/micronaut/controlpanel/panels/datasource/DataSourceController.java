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

import io.micronaut.context.BeanLocator;
import io.micronaut.controlpanel.core.security.ControlPanelSecurityPaths;
import io.micronaut.controlpanel.panels.datasource.model.Column;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.ForeignKey;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.cachecontrol.CacheControl;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.views.ModelAndView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * REST controller to execute SQL queries against a specific DataSource for the Control Panel.
 * Designed to return paged query results for the Control Panel table renderer.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Controller(ControlPanelSecurityPaths.DATASOURCE)
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class DataSourceController {

    static final Argument<DataSourceService> SERVICE_ARGUMENT = Argument.of(DataSourceService.class);
    static final Argument<DataSourceControlPanel> PANEL_ARGUMENT = Argument.of(DataSourceControlPanel.class);

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceController.class);
    private static final int DEFAULT_TABLE_PAGE_SIZE = 10;
    private static final List<Integer> TABLE_PAGE_SIZES = List.of(10, 25, 50);
    private static final Pattern SIMPLE_SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Map<String, DataSourceService> services;
    private final Map<String, DataSourceControlPanel> panels;
    private final Map<String, Schema> schemas;

    private final JsonMapper jsonMapper;

    public DataSourceController(BeanLocator locator, JsonMapper jsonMapper) {
        // Map keyed by bean name (datasource name)
        this.services = locator.mapOfType(SERVICE_ARGUMENT);
        this.panels = locator.mapOfType(PANEL_ARGUMENT);
        this.schemas = computeSchemas();
        this.jsonMapper = jsonMapper;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Initialized DataSourceController with services={}, panels={}", services.keySet(), panels.keySet());
        }
    }

    /**
     * <p>Build CodeMirror SQLNamespace with normalized lowercase keys for matching.</p>
     *
     * <pre>
     * { "schema": { "table": { self: {label:"EMP", type:"table"}, children: [{label:"EMPNO", type:"column"}, ...] } } }
     * </pre>
     *
     * @param dataSource The name of the datasource
     * @return HttpResponse containing the generated schema.js JavaScript
     */
    @Get(value = "/{dataSource}/schema.js", produces = "application/javascript")
    public HttpResponse<String> schemaJs(String dataSource) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("schemaJs requested for dataSource='{}'", dataSource);
        }
        var cachedSchema = schemas.get(dataSource);
        if (cachedSchema == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No control panel found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        var schema = cachedSchema.schema;
        var defaultSchema = cachedSchema.defaultSchema;

        try {
            var schemaJson = jsonMapper.writeValueAsString(schema);
            var defaultSchemaJson = jsonMapper.writeValueAsString(defaultSchema);
            var js = "window.codemirror=window.codemirror||{};" +
                     "window.codemirror.schema=" + schemaJson + ';' +
                     "window.codemirror.defaultSchema=" + defaultSchemaJson + ';';
            if (LOG.isDebugEnabled()) {
                LOG.debug("schemaJs built for dataSource='{}' with defaultSchema='{}' and {} tables", dataSource, defaultSchema, ((Map<?, ?>) schema.getOrDefault(defaultSchema == null ? "" : defaultSchema, Map.of())).size());
            }
            return HttpResponse.ok(js)
                .contentType(MediaType.of("application/javascript"))
                .cacheControl(CacheControl.builder().noCache().build());
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to serialize schema for dataSource='{}': {}", dataSource, e.getMessage());
            }
            return HttpResponse.serverError();
        }
    }

    /**
     * Renders one page of datasource tables as an HTML fragment.
     *
     * @param dataSource The name of the datasource
     * @param page The 1-based page number
     * @param size The number of tables per page
     * @param schema The schema to filter by, or null for all schemas
     * @param search The table search term, or null for all table names
     * @return A server-rendered HTML fragment containing one table page
     */
    @Get(value = "/{dataSource}/tables", produces = MediaType.TEXT_HTML)
    public HttpResponse<?> tables(String dataSource,
                                  @Nullable @QueryValue Integer page,
                                  @Nullable @QueryValue Integer size,
                                  @Nullable @QueryValue String schema,
                                  @Nullable @QueryValue String search) {
        var panel = panels.get(dataSource);
        if (panel == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No control panel found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        var tables = panel.getBody().tables();
        var filteredTables = filterTables(tables, schema, search);
        int pageSize = normalizePageSize(size);
        int total = filteredTables.size();
        int pageCount = pageCount(total, pageSize);
        int currentPage = normalizePage(page, pageCount);
        int from = currentPage == 0 ? 0 : (currentPage - 1) * pageSize;
        int to = Math.min(total, from + pageSize);
        var pageTables = currentPage == 0 ? List.<Table>of() : filteredTables.subList(from, to);
        var databaseType = panel.getBody().dataSourceInfo().type();
        return fragment("datasource/detail-tables-page", tablesPage(pageTables, tables, databaseType, currentPage, pageCount, pageSize, total, from, to));
    }

    /**
     * Renders a single datasource table detail as an HTML fragment.
     *
     * @param dataSource The name of the datasource
     * @param schema The table schema
     * @param tableName The table name
     * @return A server-rendered HTML fragment for the selected table
     */
    @Get(value = "/{dataSource}/tables/detail", produces = MediaType.TEXT_HTML)
    public HttpResponse<?> tableDetail(String dataSource,
                                       @Nullable @QueryValue String schema,
                                       @QueryValue("table") String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return HttpResponse.badRequest("Table name is required")
                .contentType(MediaType.TEXT_HTML_TYPE);
        }
        var panel = panels.get(dataSource);
        if (panel == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No control panel found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        var tables = panel.getBody().tables();
        var table = findTable(tables, schema, tableName);
        if (table == null) {
            return HttpResponse.notFound();
        }

        var relatedTables = findRelatedTables(table, tables);
        var mermaid = MermaidUtils.generateMermaidER(relatedTables);
        var databaseType = panel.getBody().dataSourceInfo().type();
        return fragment("datasource/detail-table-detail", tableDetail(table, tables, databaseType, mermaid));
    }

    /**
     * Renders current datasource pool status as an HTML fragment.
     *
     * @param dataSource The name of the datasource
     * @return A server-rendered HTML fragment for the current pool status
     */
    @Get(value = "/{dataSource}/pool/status", produces = MediaType.TEXT_HTML)
    public HttpResponse<?> poolStatus(String dataSource) {
        var service = services.get(dataSource);
        if (service == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No service found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        var poolInfo = service.getPoolInfo();
        if (poolInfo.isEmpty()) {
            return HttpResponse.notFound();
        }
        return fragment("datasource/detail-pool-status-card", poolInfo.get());
    }

    /**
     * Execute a SQL query against the specified datasource.
     *
     * @param dataSource The name of the datasource (path parameter)
     * @param body The query request containing SQL and pagination parameters
     * @return The query results as a {@link HttpResponse} containing {@link QueryResponse}, or an error response if the query fails
     */
    @Post(value = "/{dataSource}/query", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<QueryResponse> query(String dataSource, @Body QueryRequest body) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("query requested for dataSource='{}' (start={}, length={}, draw={}) sql='{}'", dataSource, body.start, body.length, body.draw, body.sql);
        }
        var service = services.get(dataSource);
        if (service == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No service found for dataSource='{}'", dataSource);
            }
            return HttpResponse.notFound();
        }

        if (body.sql == null || body.sql.isBlank()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Empty SQL received for dataSource='{}'", dataSource);
            }
            return HttpResponse.badRequest(QueryResponse.of(body.draw, "SQL must not be empty"));
        }

        try {
            var result = service.executeQuery(body.sql, body.start == null ? 0 : body.start, body.length == null ? 10 : body.length);

            var resp = new QueryResponse(
                body.draw == null ? 1 : body.draw,
                result.total(),
                result.total(),
                result.rows(),
                result.cols(),
                result.columns(),
                null
            );
            if (LOG.isDebugEnabled()) {
                LOG.debug("query completed for dataSource='{}' -> total={}, rowsPage={}, cols={}", dataSource, result.total(), result.rows().size(), result.cols().size());
            }
            return HttpResponse.ok(resp);
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad request for dataSource='{}': {}", dataSource, e.getMessage());
            }
            return HttpResponse.badRequest(QueryResponse.of(body.draw, e.getMessage()));
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Query failed for dataSource='{}': {}", dataSource, e.getMessage());
            }
            return HttpResponse.serverError(QueryResponse.of(body.draw, e.getMessage()));
        }
    }

    private Map<String, Schema> computeSchemas() {
        Map<String, Schema> result = HashMap.newHashMap(panels.size());
        for (var panel : panels.values()) {
            var tables = panel.getBody().tables();
            var schema = new LinkedHashMap<String, Object>();
            var counts = new LinkedHashMap<String, Integer>();

            computeSchema(tables, counts, schema);

            // Choose defaultSchema (original case, most common)
            String defaultSchema = StringUtils.EMPTY_STRING;
            int max = -1;
            for (var e : counts.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    defaultSchema = e.getKey();
                }
            }
            result.put(panel.getBeanName(), new Schema(schema, defaultSchema));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void computeSchema(final List<Table> tables, final Map<String, Integer> counts, final Map<String, Object> schema) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Computing schema for {} tables", tables.size());
        }
        for (var t : tables) {
            var schemaKey = (t.schema() == null) ? "" : t.schema();
            counts.put(schemaKey, counts.getOrDefault(schemaKey, 0) + 1);

            var tablesInSchema = (LinkedHashMap<String, Object>) schema.get(schemaKey);
            if (tablesInSchema == null) {
                tablesInSchema = new LinkedHashMap<>();
                schema.put(schemaKey, tablesInSchema);
            }

            var tableLabel = t.name();

            // children: columns (emit as plain strings to match SQLNamespace array shape)
            var tableNode = getTableNode(t, tableLabel);

            tablesInSchema.put(tableLabel, tableNode);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Schema computed with {} schemas", schema.size());
        }
    }

    private static LinkedHashMap<String, Object> getTableNode(final Table t, final String tableLabel) {
        var cols = new ArrayList<String>();
        for (var c : t.columns()) {
            cols.add(c.name());
        }

        // table node with self/children to provide display label and icon/type
        var tableNode = new LinkedHashMap<String, Object>();
        var tableSelf = new LinkedHashMap<String, Object>();
        tableSelf.put("label", tableLabel);
        tableSelf.put("type", "table");
        tableNode.put("self", tableSelf);
        tableNode.put("children", cols);
        return tableNode;
    }

    private static HttpResponse<?> fragment(String view, Object model) {
        return HttpResponse.ok(new ModelAndView<>(view, model))
            .contentType(MediaType.TEXT_HTML_TYPE);
    }

    private static int normalizePageSize(@Nullable Integer size) {
        if (size == null || !TABLE_PAGE_SIZES.contains(size)) {
            return DEFAULT_TABLE_PAGE_SIZE;
        }
        return size;
    }

    private static int pageCount(int total, int pageSize) {
        if (total == 0) {
            return 0;
        }
        return (total + pageSize - 1) / pageSize;
    }

    private static int normalizePage(@Nullable Integer page, int pageCount) {
        if (pageCount == 0) {
            return 0;
        }
        if (page == null || page < 1) {
            return 1;
        }
        return Math.min(page, pageCount);
    }

    private static TablesPage tablesPage(List<Table> tables,
                                         List<Table> allTables,
                                         DatabaseType databaseType,
                                         int page,
                                         int pageCount,
                                         int pageSize,
                                         int total,
                                         int from,
                                         int to) {
        var rows = tables.stream()
            .map(table -> tableRow(table, allTables, databaseType))
            .toList();
        var pageSizes = TABLE_PAGE_SIZES.stream()
            .map(value -> new PageSizeOption(value, value == pageSize))
            .toList();
        return new TablesPage(
            page,
            pageCount,
            tablePageSummary(page, total, from, to),
            tablePageRange(page, total, from, to),
            pageSizes,
            rows,
            rows.isEmpty(),
            page <= 1,
            page == 0 || page >= pageCount
        );
    }

    private static TableRow tableRow(Table table, List<Table> tables, DatabaseType databaseType) {
        String schema = valueOrEmpty(table.schema());
        return new TableRow(
            schema,
            valueOrEmpty(table.name()),
            schema.isBlank(),
            columns(table).size(),
            foreignKeys(table).size(),
            selectAllSql(table),
            jsonQuerySql(table, databaseType),
            relationshipJsonQuerySql(table, tables, databaseType)
        );
    }

    private static TableDetail tableDetail(Table table, List<Table> tables, DatabaseType databaseType, String mermaid) {
        var columnRows = columns(table).stream()
            .map(column -> columnDetail(table, column))
            .toList();
        var relationshipRows = foreignKeys(table).stream()
            .map(DataSourceController::relationshipDetail)
            .toList();
        return new TableDetail(
            valueOrEmpty(table.schema()),
            valueOrEmpty(table.name()),
            selectAllSql(table),
            jsonQuerySql(table, databaseType),
            relationshipJsonQuerySql(table, tables, databaseType),
            columnRows,
            columnRows.isEmpty(),
            relationshipRows,
            relationshipRows.isEmpty(),
            valueOrEmpty(mermaid)
        );
    }

    private static ColumnDetail columnDetail(Table table, Column column) {
        boolean primaryKey = column.isPrimaryKey();
        boolean foreignKey = column.isForeignKey();
        boolean unique = !primaryKey && uniqueColumns(table).contains(column.name());
        return new ColumnDetail(
            valueOrEmpty(column.name()),
            column.displayType(),
            valueOrEmpty(column.nullable()),
            primaryKey,
            foreignKey,
            unique,
            !primaryKey && !foreignKey && !unique
        );
    }

    private static RelationshipDetail relationshipDetail(ForeignKey foreignKey) {
        return new RelationshipDetail(
            valueOrEmpty(foreignKey.name()),
            valueOrEmpty(foreignKey.fkColumn()),
            valueOrEmpty(foreignKey.pkSchema()),
            valueOrEmpty(foreignKey.pkTable()),
            valueOrEmpty(foreignKey.pkColumn()),
            qualifiedDisplayName(foreignKey.pkSchema(), foreignKey.pkTable())
        );
    }

    private static String tablePageSummary(int page, int total, int from, int to) {
        if (page == 0) {
            return "No tables found.";
        }
        return "Showing " + (from + 1) + "-" + to + " of " + total + " tables";
    }

    private static String tablePageRange(int page, int total, int from, int to) {
        if (page == 0) {
            return "Showing 0 of 0 tables";
        }
        return "Showing " + (from + 1) + "-" + to + " of " + total;
    }

    private static List<Table> filterTables(List<Table> tables, @Nullable String schema, @Nullable String search) {
        var searchTerm = normalizeSearch(search);
        if (schema == null && searchTerm == null) {
            return tables;
        }
        var filtered = new ArrayList<Table>();
        for (Table table : tables) {
            if ((schema == null || valueOrEmpty(table.schema()).equals(schema))
                && matchesSearch(table, searchTerm)) {
                filtered.add(table);
            }
        }
        return filtered;
    }

    private static boolean matchesSearch(Table table, @Nullable String searchTerm) {
        if (searchTerm == null) {
            return true;
        }
        return valueOrEmpty(table.name()).toLowerCase(Locale.ROOT).contains(searchTerm)
            || valueOrEmpty(table.schema()).toLowerCase(Locale.ROOT).contains(searchTerm)
            || qualifiedDisplayName(table).toLowerCase(Locale.ROOT).contains(searchTerm);
    }

    @Nullable
    private static String normalizeSearch(@Nullable String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private static Table findTable(List<Table> tables, @Nullable String schema, String tableName) {
        for (Table table : tables) {
            if (sameTable(table, schema, tableName)) {
                return table;
            }
        }
        return null;
    }

    private static List<Table> findRelatedTables(Table selected, List<Table> tables) {
        var related = new ArrayList<Table>();
        addRelatedTable(related, selected);
        for (ForeignKey foreignKey : foreignKeys(selected)) {
            var referenced = findTable(tables, foreignKey.pkSchema(), foreignKey.pkTable());
            if (referenced != null) {
                addRelatedTable(related, referenced);
            }
        }
        for (Table candidate : tables) {
            if (sameTable(candidate, selected.schema(), selected.name())) {
                continue;
            }
            for (ForeignKey foreignKey : foreignKeys(candidate)) {
                if (sameTable(selected, foreignKey.pkSchema(), foreignKey.pkTable())) {
                    addRelatedTable(related, candidate);
                    break;
                }
            }
        }
        return related;
    }

    private static void addRelatedTable(List<Table> related, Table table) {
        for (Table existing : related) {
            if (sameTable(existing, table.schema(), table.name())) {
                return;
            }
        }
        related.add(table);
    }

    private static boolean sameTable(Table table, @Nullable String schema, String tableName) {
        return valueOrEmpty(table.schema()).equals(valueOrEmpty(schema))
            && valueOrEmpty(table.name()).equals(tableName);
    }

    private static List<Column> columns(Table table) {
        return table.columns() == null ? List.of() : table.columns();
    }

    private static List<ForeignKey> foreignKeys(Table table) {
        return table.foreignKeys() == null ? List.of() : table.foreignKeys();
    }

    private static Set<String> uniqueColumns(Table table) {
        return table.uniqueColumns() == null ? Set.of() : table.uniqueColumns();
    }

    private static String selectAllSql(Table table) {
        return "SELECT * FROM " + sqlQualifiedName(table);
    }

    @Nullable
    private static String jsonQuerySql(Table table, @Nullable DatabaseType databaseType) {
        if (databaseType == null) {
            return null;
        }
        return switch (databaseType) {
            case POSTGRES -> "SELECT to_json(x) AS json FROM " + sqlQualifiedName(table) + " x";
            case ORACLE -> "SELECT JSON_OBJECT(* RETURNING JSON) AS json FROM " + sqlQualifiedName(table);
            default -> null;
        };
    }

    @Nullable
    private static String relationshipJsonQuerySql(Table table, List<Table> tables, @Nullable DatabaseType databaseType) {
        if (databaseType == null) {
            return null;
        }
        var relationships = relationshipJsonProperties(table, tables);
        if (relationships.isEmpty()) {
            return null;
        }
        return switch (databaseType) {
            case POSTGRES -> postgresRelationshipJsonQuerySql(table, relationships);
            case ORACLE -> oracleRelationshipJsonQuerySql(table, relationships);
            default -> null;
        };
    }

    private static List<RelationshipJsonProperty> relationshipJsonProperties(Table table, List<Table> tables) {
        var relationships = new ArrayList<RelationshipJsonProperty>();
        for (ForeignKey foreignKey : foreignKeys(table)) {
            var referenced = findTable(tables, foreignKey.pkSchema(), foreignKey.pkTable());
            if (referenced != null
                && !valueOrEmpty(foreignKey.fkColumn()).isBlank()
                && !valueOrEmpty(foreignKey.pkColumn()).isBlank()) {
                relationships.add(new RelationshipJsonProperty(referenced, foreignKey, false));
            }
        }
        for (Table candidate : tables) {
            for (ForeignKey foreignKey : foreignKeys(candidate)) {
                if (sameTable(table, foreignKey.pkSchema(), foreignKey.pkTable())
                    && !valueOrEmpty(foreignKey.fkColumn()).isBlank()
                    && !valueOrEmpty(foreignKey.pkColumn()).isBlank()) {
                    relationships.add(new RelationshipJsonProperty(candidate, foreignKey, true));
                }
            }
        }
        return relationships;
    }

    private static String postgresRelationshipJsonQuerySql(Table table, List<RelationshipJsonProperty> relationships) {
        return "SELECT to_jsonb(t) || " + postgresRelationshipsJsonSql(relationships) + " AS json FROM " + sqlQualifiedName(table) + " t";
    }

    private static String postgresRelationshipsJsonSql(List<RelationshipJsonProperty> relationships) {
        var parts = new ArrayList<String>();
        for (int i = 0; i < relationships.size(); i++) {
            var relationship = relationships.get(i);
            String alias = relationship.toMany() ? "c" + (i + 1) : "r" + (i + 1);
            String value = relationship.toMany()
                ? postgresChildRowsSql(relationship, alias)
                : postgresRelatedRowSql(relationship, alias);
            parts.add(sqlStringLiteral(relationshipJsonKey(relationship, relationships)) + ", " + value);
        }
        return "jsonb_build_object(" + String.join(", ", parts) + ")";
    }

    private static String postgresRelatedRowSql(RelationshipJsonProperty relationship, String alias) {
        return "(SELECT to_jsonb(" + alias + ") FROM " + sqlQualifiedName(relationship.table()) + " " + alias +
            " WHERE " + qualifiedColumn(alias, relationship.foreignKey().pkColumn()) + " = " + qualifiedColumn("t", relationship.foreignKey().fkColumn()) +
            " LIMIT 1)";
    }

    private static String postgresChildRowsSql(RelationshipJsonProperty relationship, String alias) {
        return "COALESCE((SELECT jsonb_agg(to_jsonb(" + alias + ")) FROM " + sqlQualifiedName(relationship.table()) + " " + alias +
            " WHERE " + qualifiedColumn(alias, relationship.foreignKey().fkColumn()) + " = " + qualifiedColumn("t", relationship.foreignKey().pkColumn()) +
            "), '[]'::jsonb)";
    }

    private static String oracleRelationshipJsonQuerySql(Table table, List<RelationshipJsonProperty> relationships) {
        return "SELECT JSON_OBJECT(t.*, " +
            String.join(", ", oracleRelationshipJsonObjectProperties(relationships)) +
            " RETURNING JSON) AS json FROM " + sqlQualifiedName(table) + " t";
    }

    private static List<String> oracleRelationshipJsonObjectProperties(List<RelationshipJsonProperty> relationships) {
        var parts = new ArrayList<String>();
        for (int i = 0; i < relationships.size(); i++) {
            var relationship = relationships.get(i);
            String alias = relationship.toMany() ? "c" + (i + 1) : "r" + (i + 1);
            String value = relationship.toMany()
                ? oracleChildRowsSql(relationship, alias)
                : oracleRelatedRowSql(relationship, alias);
            parts.add(sqlStringLiteral(relationshipJsonKey(relationship, relationships)) + " VALUE " + value + " FORMAT JSON");
        }
        return parts;
    }

    private static String oracleRelatedRowSql(RelationshipJsonProperty relationship, String alias) {
        return "(SELECT " + oracleJsonObjectSql(alias) + " FROM " + sqlQualifiedName(relationship.table()) + " " + alias +
            " WHERE " + qualifiedColumn(alias, relationship.foreignKey().pkColumn()) + " = " + qualifiedColumn("t", relationship.foreignKey().fkColumn()) +
            " FETCH FIRST 1 ROW ONLY)";
    }

    private static String oracleChildRowsSql(RelationshipJsonProperty relationship, String alias) {
        return "COALESCE((SELECT JSON_ARRAYAGG(" + oracleJsonObjectSql(alias) + " RETURNING JSON) FROM " +
            sqlQualifiedName(relationship.table()) + " " + alias + " WHERE " +
            qualifiedColumn(alias, relationship.foreignKey().fkColumn()) + " = " + qualifiedColumn("t", relationship.foreignKey().pkColumn()) +
            "), JSON_ARRAY(RETURNING JSON))";
    }

    private static String oracleJsonObjectSql(String alias) {
        return "JSON_OBJECT(" + alias + ".* RETURNING JSON)";
    }

    private static String relationshipJsonKey(RelationshipJsonProperty relationship) {
        String name = valueOrEmpty(relationship.foreignKey().name());
        return name.isBlank() ? qualifiedDisplayName(relationship.table()) : name;
    }

    private static String relationshipJsonKey(RelationshipJsonProperty relationship, List<RelationshipJsonProperty> relationships) {
        String key = relationshipJsonKey(relationship);
        long matches = relationships.stream()
            .map(DataSourceController::relationshipJsonKey)
            .filter(key::equals)
            .count();
        if (matches <= 1) {
            return key;
        }
        return key + (relationship.toMany() ? "_many" : "_one");
    }

    private static String qualifiedColumn(String alias, String column) {
        return alias + "." + sqlIdentifier(column);
    }

    private static String sqlQualifiedName(Table table) {
        String schema = valueOrEmpty(table.schema());
        if (schema.isBlank()) {
            return sqlIdentifier(table.name());
        }
        return sqlIdentifier(schema) + "." + sqlIdentifier(table.name());
    }

    private static String sqlIdentifier(String value) {
        String identifier = valueOrEmpty(value);
        if (SIMPLE_SQL_IDENTIFIER.matcher(identifier).matches()) {
            return identifier;
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String sqlStringLiteral(String value) {
        return "'" + valueOrEmpty(value).replace("'", "''") + "'";
    }

    private static String qualifiedDisplayName(Table table) {
        return qualifiedDisplayName(table.schema(), table.name());
    }

    private static String qualifiedDisplayName(@Nullable String schema, String tableName) {
        String schemaName = valueOrEmpty(schema);
        if (schemaName.isBlank()) {
            return valueOrEmpty(tableName);
        }
        return schemaName + "." + valueOrEmpty(tableName);
    }

    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private record RelationshipJsonProperty(Table table, ForeignKey foreignKey, boolean toMany) {
    }

    /**
     * View model for one page of datasource tables.
     *
     * @param page             Current 1-based page number
     * @param pageCount        Total page count
     * @param summary          Human-readable page summary
     * @param range            Human-readable result range
     * @param pageSizes        Available page-size options
     * @param tables           Rows rendered on this page
     * @param empty            Whether the page has no rows
     * @param previousDisabled Whether previous-page navigation is disabled
     * @param nextDisabled     Whether next-page navigation is disabled
     */
    @ReflectiveAccess
    public record TablesPage(int page,
                             int pageCount,
                             String summary,
                             String range,
                             List<PageSizeOption> pageSizes,
                             List<TableRow> tables,
                             boolean empty,
                             boolean previousDisabled,
                             boolean nextDisabled) {
    }

    /**
     * View model for a table page-size option.
     *
     * @param value    Page-size value
     * @param selected Whether this option is selected
     */
    @ReflectiveAccess
    public record PageSizeOption(int value, boolean selected) {
    }

    /**
     * View model for a datasource table row.
     *
     * @param schema              Table schema
     * @param name                Table name
     * @param defaultSchema       Whether the table uses the default schema label
     * @param columnCount         Number of columns
     * @param relationshipCount   Number of foreign-key relationships
     * @param selectAllSql        SQL query that selects all rows from the table
     * @param jsonQuerySql        SQL query that selects rows as JSON, if supported
     * @param relationshipJsonSql SQL query that selects rows as JSON with relationships, if supported
     */
    @ReflectiveAccess
    public record TableRow(String schema,
                           String name,
                           boolean defaultSchema,
                           int columnCount,
                           int relationshipCount,
                           String selectAllSql,
                           @Nullable String jsonQuerySql,
                           @Nullable String relationshipJsonSql) {
    }

    /**
     * View model for the selected datasource table detail.
     *
     * @param schema             Table schema
     * @param name               Table name
     * @param selectAllSql       SQL query that selects all rows from the table
     * @param jsonQuerySql       SQL query that selects rows as JSON, if supported
     * @param relationshipJsonSql SQL query that selects rows as JSON with relationships, if supported
     * @param columns            Column details
     * @param columnsEmpty       Whether the table has no columns
     * @param relationships      Relationship details
     * @param relationshipsEmpty Whether the table has no relationships
     * @param mermaid            Mermaid ER diagram source
     */
    @ReflectiveAccess
    public record TableDetail(String schema,
                              String name,
                              String selectAllSql,
                              @Nullable String jsonQuerySql,
                              @Nullable String relationshipJsonSql,
                              List<ColumnDetail> columns,
                              boolean columnsEmpty,
                              List<RelationshipDetail> relationships,
                              boolean relationshipsEmpty,
                              String mermaid) {
    }

    /**
     * View model for a selected table column.
     *
     * @param name        Column name
     * @param displayType Display type label
     * @param nullable    Nullable label
     * @param primaryKey  Whether the column is part of the primary key
     * @param foreignKey  Whether the column is part of a foreign key
     * @param unique      Whether the column has a unique constraint
     * @param noKeys      Whether no key badges should be rendered
     */
    @ReflectiveAccess
    public record ColumnDetail(String name,
                               String displayType,
                               String nullable,
                               boolean primaryKey,
                               boolean foreignKey,
                               boolean unique,
                               boolean noKeys) {
    }

    /**
     * View model for a selected table relationship.
     *
     * @param name            Foreign-key name
     * @param fkColumn        Foreign-key column on the selected table
     * @param pkSchema        Referenced table schema
     * @param pkTable         Referenced table name
     * @param pkColumn        Referenced table column
     * @param pkQualifiedName Referenced table display name
     */
    @ReflectiveAccess
    public record RelationshipDetail(String name,
                                     String fkColumn,
                                     String pkSchema,
                                     String pkTable,
                                     String pkColumn,
                                     String pkQualifiedName) {
    }

    /**
     * Request DTO for query execution.
     *
     * @param sql    SQL to execute (SELECT/WITH only)
     * @param start  Pagination start offset
     * @param length Page size
     * @param draw   Client request sequence counter
     */
    @Introspected
    public record QueryRequest(String sql, Integer start, Integer length, Integer draw) { }

    /**
     * Response DTO for paged query results with column labels.
     *
     * @param draw            Client request sequence counter (echoed)
     * @param recordsTotal    Total number of rows
     * @param recordsFiltered Total number of rows after filtering (same as total)
     * @param data            Page rows (arrays of strings)
     * @param cols            Column labels used to render headers
     * @param columns         JDBC metadata for each result column
     * @param error           Optional error message
     */
    @Serdeable
    public record QueryResponse(Integer draw,
                                       int recordsTotal,
                                       int recordsFiltered,
                                       List<List<String>> data,
                                       List<String> cols,
                                       List<DataSourceService.QueryColumn> columns,
                                       String error) {
        static QueryResponse of(Integer draw, String message) {
            return new QueryResponse(
                draw == null ? 1 : draw,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                message == null ? "Error" : message
            );
        }
    }

    record Schema(Map<String, Object> schema, String defaultSchema) {
    }
}
