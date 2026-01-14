# MODULE KNOWLEDGE BASE: doc-examples/example-java

Generated: 2026-01-14T12:56:00+01:00
Commit: d5ed20d
Branch: 2.0.x

## OVERVIEW
Runnable demo Java application showcasing Micronaut Control Panel features. Wires all panels (management, cache, datasource, object storage) with sample data and configurations. Includes custom panel, health indicator, and E2E tests using Playwright. Demonstrates cache providers (Caffeine, Hazelcast, Infinispan), JDBC datasources (Postgres, Oracle), and UI interactions.

## STRUCTURE
```
doc-examples/example-java/
├── src/main/java/example/         # Application code and custom components
├── src/main/resources/            # Configs, SQL init scripts, Handlebars templates, native-image props
├── src/test/java/example/         # Unit tests
├── src/test/java/example/e2e/     # Playwright E2E tests
├── src/test/native-image/filters/ # Native image filters for Playwright and Handlebars
└── build.gradle.kts               # Module build with dependencies and test resources
```

## WHERE TO LOOK
- App entry: src/main/java/example/Application.java (Micronaut.run, cache init)
- Controllers: src/main/java/example/DemoController.java (sample endpoints)
- Custom components: src/main/java/example/MyApplicationControlPanel.java, CustomHealthIndicator.java
- Data init: src/main/java/example/SampleDataLoader.java (SQL scripts loader for datasources)
- Configs: src/main/resources/application.yml (datasources, caches, endpoints); application-hazelcast.yml, application-infinispan.yml (profile-specific)
- Templates: src/main/resources/views/my-application/ (Handlebars for custom panel UI)
- Tests: src/test/java/example/e2e/ (Playwright E2E for UI panels)
- Native image: src/main/resources/META-INF/native-image/ (properties); src/test/native-image/filters/ (Playwright, Handlebars)

## CODE MAP
- Application (class): src/main/java/example/Application.java — Main entry with Micronaut.run; inner CacheInitializer for cache population on startup
- DemoController (class): src/main/java/example/DemoController.java — Sample HTTP endpoints for routes panel demo
- MyApplicationControlPanel (class): src/main/java/example/MyApplicationControlPanel.java — Custom panel with Handlebars-rendered UI and data
- CustomHealthIndicator (class): src/main/java/example/CustomHealthIndicator.java — Custom health check for health panel
- SampleDataLoader (class): src/main/java/example/SampleDataLoader.java — Loads SQL schemas/data for Postgres/Oracle on startup/shutdown

## TESTS
- Frameworks: JUnit 5 for unit/integration; Playwright for E2E browser testing of control panel UI
- Key classes: MyApplicationControlPanelTest (unit); AbstractE2ETest (E2E base with Playwright setup); ControlPanelE2ETest (full panel UI tests); ControlPanelShutdownE2ETest (shutdown via UI)
- E2E setup: Uses embedded Micronaut server on random port; headless in CI (via CI env var); provisions test DBs via Micronaut Test Resources
- Native image: Filters ensure Playwright/Handlebars work in native builds; some datasource tests disabled in native mode

## COMMANDS
- Run app: ./gradlew :doc-examples:example-java:run (starts on port 8080; control panel at /control-panel)
- Run with profile: ./gradlew :doc-examples:example-java:run -Pmicronaut.environments=hazelcast (enables specific cache)
- Run tests: ./gradlew :doc-examples:example-java:test
- Run E2E only: ./gradlew :doc-examples:example-java:test --tests "*E2ETest"
- Build native image: ./gradlew :doc-examples:example-java:nativeCompile
- Environment notes: Requires Docker for test DBs (Oracle/Postgres via Test Resources); Playwright browsers installed (npx playwright install); ports: app 8080, Hazelcast 5701, Infinispan 11222 (configurable)

## ANTI-PATTERNS (THIS MODULE)
- Avoid modifying SQL init scripts without updating SampleDataLoader
- Don't duplicate panel wiring—use app as reference for integrations
- Keep E2E tests focused on UI flows; use unit tests for component logic

## NOTES
- Demonstrates all control panel panels with real data (e.g., ER diagrams for datasources)
- Native image filters reference: playwright-filter.json and handlebars-filter.json for test compatibility
- Runs in 'test' environment for E2E, with random ports to avoid conflicts
