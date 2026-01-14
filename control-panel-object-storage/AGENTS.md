# MODULE KNOWLEDGE BASE: control-panel-object-storage

Generated: 2026-01-14T12:56:00+01:00
Commit: d5ed20d
Branch: 2.0.x

## OVERVIEW
Micronaut Control Panel module providing UI panels for object storage providers (AWS S3, Google Cloud Storage, Azure Blob Storage, Local, Oracle). Enables listing objects within configured storages, viewing metadata (e.g., bucket/container/path), generating download links, and basic operations like upload/delete. Uses provider-agnostic abstractions for seamless integration across multiple storage configs.

## WHERE TO LOOK
- Core panel logic and abstractions: control-panel-object-storage/src/main/java/io/micronaut/controlpanel/panels/objectstorage
- Controller for API endpoints: control-panel-object-storage/src/main/java/io/micronaut/controlpanel/panels/objectstorage/ObjectStorageController.java
- Configuration and enabling: control-panel-object-storage/src/main/java/io/micronaut/controlpanel/panels/objectstorage/package-info.java
- Tests: control-panel-object-storage/src/test/groovy/io/micronaut/controlpanel/panels/objectstorage

## CODE MAP
- ObjectStorageControlPanel (class): control-panel-object-storage/.../objectstorage/ObjectStorageControlPanel.java — Main panel implementation extending AbstractEachBeanControlPanel. Handles listing objects via ObjectStorageOperations.listObjects(), filtering retrievable entries, computing provider-specific metadata (e.g., bucket for S3/GCS, path for Local), and badge count for object totals. Uses @EachBean(AbstractObjectStorageConfiguration.class) for one panel per config.
- ObjectStorageController (class): control-panel-object-storage/.../objectstorage/ObjectStorageController.java — Internal controller (@Internal) at /object-storage-control-panel-controller providing endpoints for basic ops: GET /{objectStorage}/{key} for download (streams as StreamedFile with link generation via HttpHostResolver), POST /{objectStorage} for upload (multipart), DELETE /{objectStorage}/{key} for deletion. Locates ObjectStorageOperations via BeanLocator.

## CONVENTIONS
- Provider-agnostic design: Leverages generic ObjectStorageOperations<?, ?, ?> for CRUD ops, supporting S3, GCS, Azure, Local, and Oracle without direct dependencies. Metadata computation uses instanceof checks on AbstractObjectStorageConfiguration for details like "bucket" (S3/GCS) or "container" (Azure).
- Enabling: Package-level @Requires(property = ObjectStorageControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE) and @Requires(condition = ControlPanelEnabledCondition.class) to conditionally enable panels. Note: While not directly on ObjectStorageClient, these ensure client-dependent operations (e.g., listing buckets/objects) are only active when configured.
- Multi-config support: @EachBean creates panels per storage config; icons adapt per provider (e.g., AWS for S3).
- Follows project standards: Javadoc with @author/@since, @TypeHint for GraalVM compatibility on provider-specific types.

## TESTS
- Spock: Extensive specs in ObjectStorageControlPanelSpec.groovy using Spock framework (Groovy). Covers panel naming/icons/views/categories, body retrieval (object listing and filtering), badge computation, metadata for providers (e.g., AWS bucket, Local path), enable/disable via properties, and multi-panel creation for multiple configs. Uses mocks for ObjectStorageOperations to simulate listing/retrieval.

## ANTI-PATTERNS (THIS MODULE)
- Avoid adding provider-specific logic outside computeMetadata() or getIcon()—keep abstractions generic to maintain S3/GCS/Azure compatibility.
- Don't hardcode bucket/container names; rely on configuration and operations for listing objects and metadata.
- Steer clear of direct ObjectStorageClient usage without @Requires checks—use operations layer for indirection and conditional enabling.

## NOTES
- Focuses on object-level ops (listing, metadata view, download links via controller); bucket listing is derived from config metadata.
- Integrates with core control panel registry for UI rendering.