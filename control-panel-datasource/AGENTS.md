# control-panel-datasource Agent Guidance

This module owns JDBC datasource panels for schema inspection, SQL querying, and Mermaid ER diagrams.

## Where To Work

- Metadata and query service: `src/main/java/io/micronaut/controlpanel/panels/datasource/DataSourceService.java`
- HTTP API: `src/main/java/io/micronaut/controlpanel/panels/datasource/DataSourceController.java`
- Panel implementation: `src/main/java/io/micronaut/controlpanel/panels/datasource/DataSourceControlPanel.java`
- Schema models: `src/main/java/io/micronaut/controlpanel/panels/datasource/model`
- Mermaid generation: `src/main/java/io/micronaut/controlpanel/panels/datasource/MermaidUtils.java`
- Templates: `src/main/resources/controlpanelviews/datasource`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/datasource`

## Rules

- Keep JDBC metadata access in `DataSourceService`; callers should not duplicate metadata traversal or query execution.
- Preserve query sanitization, pagination, and SELECT-only behavior unless the issue explicitly changes query safety.
- Use `DatabaseMetaData` for tables, columns, keys, and related schema information.
- Keep database-specific differences behind `DatabaseType`, `ColumnType`, or focused helper methods.
- Keep client-side editor assumptions aligned with `control-panel-ui` and the generated schema endpoint.
- Keep `/datasource/{name}/query` and `/datasource/{name}/schema.js` route behavior aligned with the SQL editor UI.
- When changing Mermaid output, test the generated syntax and expected labels rather than only snapshots.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-datasource:test`
- Run example or browser tests only when UI behavior, schema endpoint output, or datasource interaction flows change.
