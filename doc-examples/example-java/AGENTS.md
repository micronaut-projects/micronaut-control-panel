# doc-examples/example-java Agent Guidance

This module is the runnable Java example application used by the guide and higher-level UI tests.

## Where To Work

- Application entry point: `src/main/java/example/Application.java`
- Sample endpoints and data: `src/main/java/example`
- Custom panel example: `src/main/java/example/MyApplicationControlPanel.java`
- Configuration: `src/main/resources/application.yml` and environment-specific YAML files
- Custom panel templates: `src/main/resources/views/my-application`
- Unit and integration tests: `src/test/java/example`
- Browser tests: `src/test/java/example/e2e`
- Native-image test filters: `src/test/native-image/filters`

## Rules

- Keep this module as an example of library usage, not a source of reusable control panel infrastructure.
- Keep snippets used by the guide runnable and synchronized with source tags.
- When changing sample schemas or data, update `SampleDataLoader` and affected tests together.
- Keep E2E tests focused on user-visible control panel flows; use unit tests for component-level behavior.
- Preserve Test Resources assumptions for datasources and cache providers, and call out Docker requirements when validation depends on them.
- Keep native-image filters aligned with Playwright and Handlebars usage when those areas change.

## Verification

- Targeted tests: `./gradlew :micronaut-doc-examples:micronaut-example-java:test`
- Example run: `./gradlew :micronaut-doc-examples:micronaut-example-java:run`
- Run browser-focused tests when changing visible control panel flows or example wiring.
