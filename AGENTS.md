# Micronaut Control Panel Agent Guidance

This repository builds the Micronaut Control Panel libraries and example application. The control panel is a development-time web UI for inspecting and managing Micronaut application state.

## Scope

- Root guidance applies across the repository.
- Module-level `AGENTS.md` files add narrower rules for their directory tree.
- Prefer the closest `AGENTS.md` when instructions overlap, then fall back to this file.

## Project Layout

- `control-panel-bom/`: dependency alignment only.
- `control-panel-core/`: control panel contracts, repository, configuration, enablement conditions, and built-in panels.
- `control-panel-management/`: panels backed by Micronaut management endpoints.
- `control-panel-cache/`: cache provider panels and cache invalidation controller.
- `control-panel-datasource/`: JDBC datasource schema, query, and Mermaid ER panels.
- `control-panel-object-storage/`: object storage listing, upload, download, and delete panels.
- `control-panel-ui/`: HTTP UI controller, Handlebars helpers/templates, CSS, and bundled JavaScript assets.
- `control-panel-kafka/`: Kafka Streams topology panel.
- `doc-examples/example-java/`: runnable example app and browser-oriented tests.
- `src/main/docs/guide/`: AsciiDoc user guide and navigation.
- `buildSrc/`: Gradle convention plugins; put shared build logic here rather than in module builds.

## Working Rules

- Treat modules as libraries. Do not add application-only behavior outside `doc-examples/`.
- Keep panel-specific behavior inside the owning module; shared contracts belong in `control-panel-core`.
- Do not add ad-hoc build logic to module `build.gradle.kts` files when a `buildSrc` convention is the right home.
- If you add or change dependencies, keep BOM alignment and version-catalog usage consistent with existing Gradle conventions.
- Keep controllers, panels, templates, and tests in the package/domain already used by the module.
- Prefer targeted module tests for code changes. Run the full `check` task only when shared behavior, build logic, or cross-module contracts changed.

## UI Design System

- The Control Panel UI follows [shadcn/ui](https://ui.shadcn.com/) component and block patterns, implemented with server-rendered Handlebars templates, static CSS, and small JavaScript helpers rather than React runtime components.
- Prefer shadcn/ui component vocabulary and behavior for new UI: `Card`, `Button`, `Badge`, `Dialog`, `Dropdown Menu`, `Data Table`, `Tabs`, `Breadcrumb`, `Sidebar`, `Hover Card`, and `Table`.
- Use the existing semantic CSS tokens in `control-panel-ui/src/main/resources/static/css/dashboard.css` (`--background`, `--foreground`, `--card`, `--border`, `--sidebar`, `--muted`, `--accent`, and related foreground tokens) instead of adding one-off colors.
- Keep dark and light themes structurally identical. Theme changes should swap tokens and assets, not remove borders, spacing, shadows, or sidebar/card layout.
- Preserve accessibility contracts: semantic buttons and links, labels for icon-only controls, visible focus states, keyboard-usable dialogs/menus/tables, and readable text contrast in both themes.
- Prefer existing partials/classes and local shadcn-style patterns before adding new CSS. If new styling is required, keep it token-based and scoped to the component or panel.
- Verify substantial UI work in a browser at desktop and mobile widths, including collapsed sidebar state and both color schemes.

## Documentation System

- User-guide sources live in `src/main/docs/guide`; `toc.yml` is the guide navigation source of truth.
- Release notes are maintained in `src/main/docs/guide/releaseHistory.adoc`.
- Shared guide images live in `src/main/docs/resources/img/`.
- Use PNG screenshots for guide images. Prefer a consistent `1200px` image width: `1200 x 800` for full panel screenshots and about `1200 x 520` to `1200 x 600` for wide overview screenshots.
- Only use larger screenshots, up to about `1600px` wide, when dense dashboard or table content becomes unreadable at `1200px`.
- Crop unnecessary browser chrome and vertical whitespace, capture at 100% browser zoom, and keep documentation screenshots reasonably small, ideally under about `250 KB` when image quality remains readable.
- Prefer validated snippets from `doc-examples/` and generated configuration property includes over hand-maintained prose or tables.
- Use `./gradlew publishGuide` for guide assembly and `./gradlew docs` when API docs also matter.

## Common Commands

- List Gradle project paths: `./gradlew -q projects`
- Full verification: `./gradlew check`
- Format check/apply: `./gradlew spotlessCheck` / `./gradlew spotlessApply`
- Guide docs: `./gradlew publishGuide`
- Guide plus API docs: `./gradlew docs`
- Lightweight text-only validation: `git diff --check`

Use standardized Gradle project paths, for example `:micronaut-control-panel-core:test` or `:micronaut-doc-examples:micronaut-example-java:test`.
