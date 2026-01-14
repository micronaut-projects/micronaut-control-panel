# PROJECT KNOWLEDGE BASE

Generated: 2026-01-14T12:56:00+01:00
Commit: d5ed20d
Branch: 2.0.x

## OVERVIEW
This is the **Micronaut Control Panel** module, which provides a web UI that allows you to view and manage the state of
your Micronaut application, typically in a development environment. This is a multi-module Gradle project that is part
of the Micronaut Framework ecosystem.

**Key Points:**
- This is a **library module**, not an application - modules are designed to be used as dependencies
- The root project contains no code and serves as a parent project for coordination and documentation
- Project follows standard Micronaut conventions and Gradle best practices

## STRUCTURE
```
./
├── control-panel-bom/              # Version alignment (BOM) only
├── control-panel-core/             # Core control panel abstractions and shared pieces
├── control-panel-management/       # Panels for management endpoints (env, health, loggers, routes)
├── control-panel-cache/            # Panels for cache providers (Caffeine, Ehcache, Hazelcast, Infinispan)
├── control-panel-datasource/       # Panels for JDBC datasources (schema, queries, ER/mermaid)
├── control-panel-object-storage/   # Panels for object storage providers
├── control-panel-ui/               # UI assets (JS bundle), server-side Handlebars helpers
├── doc-examples/example-java/      # Runnable example app + E2E tests
├── buildSrc/                       # Gradle convention plugins (Kotlin)
├── src/main/docs/                  # AsciiDoc documentation
└── .github/workflows/              # CI (Gradle/Graal/Release/Publishing)
```

## WHERE TO LOOK
- Core contracts and registry: control-panel-core/src/main/java/io/micronaut/controlpanel/core
- UI HTTP endpoints + server-side helpers: control-panel-ui/src/main/java/io/micronaut/controlpanel/ui
- UI frontend source (bundled): control-panel-ui/src/main/javascript
- Management panels: control-panel-management/src/main/java/io/micronaut/controlpanel/panels/management
- Cache panels: control-panel-cache/src/main/java/io/micronaut/controlpanel/panels/cache
- Datasource panels: control-panel-datasource/src/main/java/io/micronaut/controlpanel/panels/datasource
- Object storage panels: control-panel-object-storage/src/main/java/io/micronaut/controlpanel/panels/objectstorage
- Example app entry point: doc-examples/example-java/src/main/java/example/Application.java
- Docs (AsciiDoc): src/main/docs/guide
- Build conventions: buildSrc/src/main/kotlin
- CI workflows: .github/workflows/*.yml

## CODE MAP
- ControlPanel (interface): control-panel-core/.../core/ControlPanel.java — central abstraction for panels
- ControlPanelController (class): control-panel-ui/.../ui/ControlPanelController.java — routes for UI/API
- Application (class): doc-examples/example-java/.../example/Application.java — demo main and startup wiring

## CONVENTIONS
- Gradle Kotlin DSL, version catalogs via Micronaut BOM and module BOM
- All Gradle projects are prefixed with `micronaut-`. E.g. if the directory name is `control-panel-core`, the project
  name is `micronaut-control-panel-core`
- Tests: JUnit 5 (Java) + Spock (Groovy) co-exist; module-specific logback and application test configs
- UI: Rollup bundles editor.mjs to static bundle; CodeMirror for SQL editor
- Templates: Handlebars with helpers registered in HandlebarsHelperRegistrar
- Controllers/Endpoints under io.micronaut.controlpanel.* per module domain

## ANTI-PATTERNS (THIS PROJECT)
- No ad-hoc build logic in module build.gradle.kts — use buildSrc convention plugins
- Avoid adding new dependencies without updating BOM alignment
- Keep panels minimal; avoid mixing cross-module concerns (panel-specific logic stays in its module)

## UNIQUE STYLES
- Datasource panel generates Mermaid ER diagrams from JDBC metadata
- Extensive Spock specs with data-driven where blocks for UI/server validations
- Example app includes Playwright-driven E2E tests and native-image filters for CI

## COMMANDS
- Build & test all: ./gradlew check
- Spotless format check/apply: ./gradlew spotlessCheck | ./gradlew spotlessApply
- Module tests: ./gradlew :micronaut-control-panel-core:test (or other module)
- GraalVM native tests: ./gradlew nativeTest
- Docs: ./gradlew docs (outputs to build/docs)

## NOTES
- CI uses GraalVM 25; Sonatype, Sonar, and GE integration wired via secrets
- Root-level src/ holds docs only; runtime code lives under modules
