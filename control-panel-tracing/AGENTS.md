# control-panel-tracing Agent Guidance

This module owns the Tracing control panel, which visualises the recent traces retained by the Micronaut Tracing trace inspector (`micronaut-tracing-opentelemetry-inspector`).

## Where To Work

- Panel, configuration, JSON helper controller and view models: `src/main/java/io/micronaut/controlpanel/panels/tracing`
- Module defaults: `src/main/resources/micronaut-control-panel`
- Templates: `src/main/resources/views/tracing`
- Styles: the `Tracing panel` section of `control-panel-ui/src/main/resources/static/css/dashboard.css`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/tracing`
- User-guide page: `src/main/docs/guide/controlPanels/tracing.adoc`

## Rules

- Keep the panel and controller conditional on a `TraceInspector` bean; the inspector is a `compileOnly` dependency that applications add themselves.
- Read traces only through the `TraceInspector` API; do not register span processors or depend on inspector internals.
- Compute waterfall positions server side in `TraceViews` so they stay covered by tests; the detail view JavaScript only renders them.
- The JSON helper routes may omit empty collections depending on the application's serialization configuration, so the detail view must treat missing arrays as empty.
- Clearing traces is a write operation routed through `ControlPanelSecurityPaths.TRACING_PATH`.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-tracing:test`
- Run `./gradlew publishGuide` when changing the tracing guide page or navigation.
