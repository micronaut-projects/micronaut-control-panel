# MODULE KNOWLEDGE BASE: control-panel-ui

Generated: 2026-01-14
Commit: d5ed20d (from root project reference)
Branch: 2.0.x

## OVERVIEW
The control-panel-ui module provides the web UI layer for Micronaut Control Panel, including server-side rendering with Handlebars, HTTP controllers for panel navigation, and bundled JavaScript assets for interactive features like SQL editing. It depends on control-panel-core for panel data and integrates with Micronaut's views-handlebars for templating. This module handles both API endpoints and HTML rendering for development-time application inspection.

## WHERE TO LOOK
- HTTP controllers and API: src/main/java/io/micronaut/controlpanel/ui (e.g., ControlPanelController for index, category, and detail endpoints)
- JavaScript assets: src/main/javascript (bundled editor for client-side interactivity)
- Handlebars templates: src/main/resources/views (*.hbs files like layout.hbs, index.hbs, detail.hbs for main UI structure)
- Utility classes: src/main/java/io/micronaut/controlpanel/ui/util (e.g., EndpointUtils for path handling)
- Build config: build.gradle.kts (uses convention plugins for UI-specific setup, dependencies like micronaut-views-handlebars)
- Tests: src/test/groovy (Spock specs) and src/test/resources (YAML configs like application-test.yml, logback.xml)

## UI BUILD/ASSETS
- JavaScript bundle generated via Rollup from editor.mjs, which sets up a CodeMirror-based SQL editor with dialect support (e.g., MySQL, PostgreSQL).
- Assets served as static resources (e.g., editor.bundle.js, dashboard.css) for client-side enhancements like syntax highlighting and autocompletion in panels (e.g., datasource queries).
- Build process: Run `rollup -c rollup.config.mjs` in src/main/javascript to generate bundles; integrated into Gradle via convention plugins.

## SERVER-SIDE HELPERS
- HandlebarsHelperRegistrar: Registers custom helpers (e.g., size, mod, minus, percentage, isMap) for template logic; configures caching and precompiles templates from views/ prefix.
- ControlPanelController: Implements ControlPanelApi with endpoints like GET /controlpanel (index), GET /controlpanel/{categoryId} (by category), GET /controlpanel/{name} (detail view); uses repository for panel data and renders via Handlebars.

## CONVENTIONS (module-specific)
- Templates use Handlebars with partials and helpers for reusable UI components; all .hbs files in src/main/resources/views follow a prefix='views/' convention for loading.
- Tests use Spock (Groovy specs) for behavior-driven testing of controllers, helpers, and utils; YAML configs in src/test/resources for test-specific setup (e.g., application-test.yml for context paths, logback.xml for logging).
- Java classes under io.micronaut.controlpanel.ui package; annotation-driven (e.g., @Controller, @View) with Jakarta inject; minimal custom logic, leveraging Micronaut's HTTP and views features.

## ANTI-PATTERNS (THIS MODULE)
- Avoid adding new JS dependencies without updating package.json and Rollup config; use existing CodeMirror setup for editors.
- Do not mix UI concerns with core panel logic (keep in core module); no direct template modifications without precompile tests.
- Steer clear of ad-hoc endpoint additions; all routes must go through ControlPanelController to maintain API consistency.
- Never skip Spock tests or YAML config verification; all changes must pass full test suite including path and caching specs.

