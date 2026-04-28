# control-panel-core Agent Guidance

This module owns the shared contracts, configuration, repository, and base classes used by every control panel module.

## Where To Work

- Core contracts and records: `src/main/java/io/micronaut/controlpanel/core`
- Configuration and enablement: `src/main/java/io/micronaut/controlpanel/core/config`
- Built-in panels and utilities: `src/main/java/io/micronaut/controlpanel/core/panels` and `src/main/java/io/micronaut/controlpanel/util`
- Built-in templates: `src/main/resources/views`
- Tests: `src/test/java/io/micronaut/controlpanel/core`
- Native metadata: `src/main/resources/META-INF/native-image`

## Rules

- Keep public panel contracts stable and small; downstream modules depend on them.
- Panels should implement `ControlPanel` or extend the existing abstract base classes.
- Use Micronaut configuration and `@Requires` conditions for enablement instead of manual runtime checks in callers.
- Keep category, view, badge, and ordering behavior consistent with `ControlPanel` and `ControlPanelConfiguration`.
- Do not instantiate panels directly in production code; rely on Micronaut bean discovery, `ControlPanelRepository`, and `ControlPanelLoader`.
- Add configuration metadata and generated docs support when introducing user-visible configuration.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-core:test`
- Broader check when contracts change: `./gradlew check`
