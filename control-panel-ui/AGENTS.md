# control-panel-ui Agent Guidance

This module owns the server-rendered UI layer, Handlebars integration, static assets, and client-side editor bundle.

## Where To Work

- HTTP controller and API surface: `src/main/java/io/micronaut/controlpanel/ui`
- Handlebars helper registration: `src/main/java/io/micronaut/controlpanel/ui/handlebars`
- UI utility code: `src/main/java/io/micronaut/controlpanel/ui/util`
- Templates and static resources: `src/main/resources/views` and `src/main/resources/static`
- JavaScript source and Rollup config: `src/main/javascript`
- Tests: `src/test/java/io/micronaut/controlpanel/ui`

## Rules

- Keep route behavior centralized in `ControlPanelController` and shared path logic in existing utilities.
- Keep panel domain logic out of this module; it should render data supplied by core and panel modules.
- Register new Handlebars helpers in `HandlebarsHelperRegistrar` and cover them with focused tests.
- Preserve the `views/` template loading convention and precompile coverage.
- For JavaScript changes, update `src/main/javascript/editor.mjs` and rebuild through the existing Rollup setup.
- Do not add JavaScript dependencies without updating `package.json` and confirming the generated bundle still works.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-ui:test`
- JavaScript bundle from `control-panel-ui/src/main/javascript`: `npm install` if needed, then `npm run build`
- Run `./gradlew check` when controller behavior or shared rendering contracts change.
