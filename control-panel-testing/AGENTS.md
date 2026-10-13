# control-panel-testing Agent Guidance

This module owns the Testing category and its Continuous Testing panel: in development mode it shows whether continuous testing (`./gradlew mnTest`, `mvn mn:test`, `pyronaut test -t`) runs for the project in a process of its own, links to its live report, summarizes the latest results, and otherwise tells how to start it in a separate terminal.

## Where To Work

- Panel implementation: `src/main/java/io/micronaut/controlpanel/panels/testing/ContinuousTestingControlPanel.java`
- Status discovery: `src/main/java/io/micronaut/controlpanel/panels/testing/ContinuousTestingStatusResolver.java`
- Configuration: `src/main/java/io/micronaut/controlpanel/panels/testing/ContinuousTestingConfiguration.java`
- Module defaults: `src/main/resources/micronaut-control-panel`
- Templates: `src/main/resources/views/continuous-testing`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/testing`
- User-guide page: `src/main/docs/guide/controlPanels/testing.adoc`

## Rules

- Keep the panel conditional on `micronaut.dev.enabled`: continuous testing runs beside an application in development mode only.
- Do not depend on `micronaut-dev`: read the report the test mode writes (`state.json`) and probe its LiveReload server instead, so the module works with the Micronaut version the application chooses.
- Only probe and link to loopback HTTP addresses, whatever a report names.
- Keep the "not running" state useful: it lists the Gradle, Maven and Pyronaut commands, the project's build tool first.
- Test with report fixtures and a local HTTP server, never with a real continuous testing process.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-testing:test`
- Run `./gradlew publishGuide` when changing the Testing guide page or navigation.
