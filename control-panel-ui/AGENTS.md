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

## UI Design Rules

- The UI is built to match [shadcn/ui](https://ui.shadcn.com/) components and dashboard/sidebar blocks. Implement those patterns with Handlebars, CSS tokens, existing JavaScript helpers, and bundled assets.
- Use shadcn/ui component semantics when naming or shaping templates: cards use header/body/footer structure, destructive actions live in dialogs, row actions use menus, large lists use data-table behavior, detail pages use cards, and navigation uses sidebar plus breadcrumb patterns.
- Keep the visual system token-driven. Use `dashboard.css` variables for backgrounds, foregrounds, borders, rings, sidebars, muted text, accent states, destructive states, and shadows; avoid hard-coded colors unless they define or intentionally adjust a token.
- Keep light and dark themes equivalent. Do not add theme-specific overrides that remove the sidebar border, card borders, radius, spacing, layout, or shadows unless the same structural change applies to both themes.
- Default theme selection should respect the browser or operating system color-scheme setting until the user explicitly selects a mode. Persist only explicit user choices.
- Prefer existing shadcn-style controls before creating new patterns: `btn`, `badge`, `card`, `cp-data-table`, `cp-sidebar`, dialogs, dropdown/action menus, tabs, breadcrumbs, hover cards, and table utilities.
- Maintain responsive behavior for desktop, mobile, and collapsed sidebar states. Text must not overflow buttons, cards, table cells, breadcrumbs, or sidebar items.
- Preserve accessibility: icon-only buttons need labels/titles, dialogs need focusable controls and clear labels, menus must remain keyboard-reachable, tables need searchable/filterable controls where expected, and contrast must work in both themes.
- Use official Micronaut logos from `src/main/resources/static/img` and switch assets through theme-aware CSS rather than embedding duplicate SVG markup in templates.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-ui:test`
- JavaScript bundle from `control-panel-ui/src/main/javascript`: `npm install` if needed, then `npm run build`
- Run `./gradlew check` when controller behavior or shared rendering contracts change.
