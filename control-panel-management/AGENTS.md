# control-panel-management Agent Guidance

This module owns control panels backed by Micronaut management endpoints such as health, environment, loggers, and beans.

## Where To Work

- Panel implementations: `src/main/java/io/micronaut/controlpanel/panels/management`
- Beans panels: `src/main/java/io/micronaut/controlpanel/panels/management/beans`
- Endpoint filtering helpers: `src/main/java/io/micronaut/controlpanel/panels/management`
- Templates: `src/main/resources/views`
- Module defaults: `src/main/resources/micronaut-control-panel`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/management`

## Rules

- Integrate through Micronaut management endpoint beans rather than duplicating endpoint behavior.
- Use `@Requires` conditions so panels are present only when their backing endpoint and configuration are available.
- Keep endpoint-specific logic isolated; do not mix health, environment, logger, and bean concerns.
- Preserve masking and filtering behavior for environment data unless a change explicitly targets it.
- Keep panel badges and bodies cheap to compute and representative of endpoint state.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-management:test`
- Run broader checks when endpoint contracts or shared panel behavior change.
