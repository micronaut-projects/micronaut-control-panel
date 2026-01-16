Module: OpenAPI (micronaut-control-panel-openapi)

Summary:
- Discovers enabled OpenAPI viewers via micronaut.openapi.views.spec.
- Infers static resource mappings from micronaut.router.static-resources.*.mapping with heuristics.
- Registers one panel per viewer under the "OpenAPI" category, linking directly to the viewer (no details).

Key classes:
- OpenApiControlPanelLoader — ControlPanelLoader that builds panels dynamically.
- OpenApiViewerControlPanel — Concrete control panel for a single viewer, using shared Handlebars template views/openapi/link.hbs

Configuration:
- Users may customize per-panel settings via:
  micronaut.control-panel.panels."<panelName>".title/icon/order/enabled
  where panelName is "openapi-<viewer>", e.g. openapi-rapidoc.

