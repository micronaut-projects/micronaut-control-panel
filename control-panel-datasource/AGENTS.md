# MODULE KNOWLEDGE BASE: control-panel-datasource

Generated: 2026-01-14T12:56:00+01:00
Commit: d5ed20d (based on root project)
Branch: 2.0.x

## OVERVIEW
Control Panel module for JDBC DataSources: provides schema visualization, ad-hoc SQL querying, and ER diagrams. Integrates with Micronaut Data for metadata extraction and safe query execution. UI via Handlebars templates with CodeMirror SQL editor (integrated from control-panel-ui module).

## STRUCTURE
```
./control-panel-datasource/
├── src/main/java/io/micronaut/controlpanel/panels/datasource/  # Core logic: service, controller, models, utils
├── src/main/resources/views/datasource/                       # Handlebars templates: body.hbs (overview), detail.hbs (SQL editor + results)
├── src/test/java/io/micronaut/controlpanel/panels/datasource/ # JUnit tests for service, controller, panel
└── build.gradle.kts                                           # Module build with JDBC dependencies
```

## WHERE TO LOOK
- Core service: src/main/java/.../datasource/DataSourceService.java — metadata fetching and query execution
- REST endpoints: src/main/java/.../datasource/DataSourceController.java — query POST and schema.js GET
- Models: src/main/java/.../datasource/model/* — Table, Column, etc. for schema representation
- ER generation: src/main/java/.../datasource/MermaidUtils.java — Mermaid diagram builder
- Templates: src/main/resources/views/datasource/detail.hbs — UI with jsTree schema tree, CodeMirror editor, DataTables results
- Tests: src/test/java/.../datasource/*Test.java — JUnit coverage for metadata, queries, panel init

## CODE MAP
- DataSourceService (class): .../datasource/DataSourceService.java — Per-DataSource bean; gets tables/columns/FKs via JDBC DatabaseMetaData; executes sanitized/paginated SQL; generates Mermaid ER.
- DataSourceController (class): .../datasource/DataSourceController.java — Handles /datasource/{name}/query (POST for SQL exec) and /datasource/{name}/schema.js (GET for CodeMirror autocomplete data).
- DataSourceControlPanel (class): .../datasource/DataSourceControlPanel.java — Panel bean per DataSource; aggregates metadata into Body model with tables and Mermaid ER.
- Table (record): .../model/Table.java — Schema, name, columns list, unique sets, foreign keys.
- Column (record): .../model/Column.java — Name, type (via ColumnType enum), size, nullable, PK/FK flags.
- ForeignKey (record): .../model/ForeignKey.java — FK name, column mappings to referenced PK table/column.
- DatabaseType (enum): .../model/DatabaseType.java — Inferred from dialect (POSTGRES, MYSQL, etc.) for icons/handling.
- ColumnType (enum): .../model/ColumnType.java — Generic types (TEXT, NUMERIC, etc.) mapped from JDBC codes.
- MermaidUtils (class): .../MermaidUtils.java — Static methods to build Mermaid ER syntax from tables/FKs.

## CONVENTIONS
- JDBC metadata: Use DatabaseMetaData for tables/columns/keys; map SQL types to ColumnType; infer DatabaseType from dialect/db-type props.
- Pagination: Queries support offset/limit; results include total count for DataTables.
- Security: Sanitize SQL by stripping comments, forbidding semicolons/non-SELECT in queries; execute via prepared statements.
- UI integration: Templates use Handlebars; CodeMirror for SQL editor with schema-based autocomplete (tables/columns); sample queries like "SELECT * FROM table" inserted on tree selection.
- Route paths: /datasource/{name}/query (POST QueryRequest → QueryResponse JSON); /datasource/{name}/schema.js (JS object for editor).

## ANTI-PATTERNS (THIS MODULE)
- Avoid direct JDBC calls outside DataSourceService — use service methods for metadata/queries to ensure sanitization.
- Don't hardcode database-specific logic — rely on DatabaseType/ColumnType for portability.
- Skip adding UI logic here — keep JS/CSS/Handlebars minimal; major UI components in control-panel-ui module.

## UNIQUE STYLES
- Mermaid ER generation: Builds diagrams with entities, attributes (w/ types/keys), and labeled relationships; sanitizes identifiers for Mermaid syntax.
- CodeMirror integration: Via detail.hbs; loads schema.js for autocomplete; supports Ctrl+Space hints and Cmd+Enter exec.
- Tests: Pure JUnit with mocks for DataSource/Connection; cover happy paths, errors, pagination, ER output.

## COMMANDS
- Test module: ./gradlew :control-panel-datasource:test
- Run example: Use doc-examples/example-java with configured DataSource

## NOTES
- Requires JDBC DataSource beans; supports multiple (e.g., default, secondary).
- ER diagrams rendered in modal via Mermaid; no real-time updates — refresh panel for changes.
- Sample queries in UI: Auto-insert on table click; editor includes instructions and error handling.
