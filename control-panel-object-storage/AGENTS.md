# control-panel-object-storage Agent Guidance

This module owns object storage panels and object-level actions for configured Micronaut Object Storage providers.

## Where To Work

- Panel implementation: `src/main/java/io/micronaut/controlpanel/panels/objectstorage/ObjectStorageControlPanel.java`
- Upload/download/delete controller: `src/main/java/io/micronaut/controlpanel/panels/objectstorage/ObjectStorageController.java`
- Package-level enablement: `src/main/java/io/micronaut/controlpanel/panels/objectstorage/package-info.java`
- Templates: `src/main/resources/views/object-storage`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/objectstorage`

## Rules

- Use `ObjectStorageOperations` and configuration abstractions; avoid direct provider clients in panel logic.
- Keep provider-specific display metadata limited to focused helper logic such as icon or metadata computation.
- Preserve one panel per storage configuration via `@EachBean`.
- Keep controller routes internal to the control panel and resolve operations through Micronaut bean lookup.
- Maintain conditional enablement so the module stays inactive when object storage is not configured.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-object-storage:test`
- Run broader checks if object storage behavior affects shared UI rendering or core panel contracts.
