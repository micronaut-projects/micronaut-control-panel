# control-panel-cache Agent Guidance

This module owns cache provider panels and cache invalidation actions for Micronaut cache integrations.

## Where To Work

- Shared cache panel behavior: `src/main/java/io/micronaut/controlpanel/panels/cache/AbstractCacheControlPanel.java`
- Static provider panels: `src/main/java/io/micronaut/controlpanel/panels/cache`
- Hazelcast support: `src/main/java/io/micronaut/controlpanel/panels/cache/hazelcast`
- Infinispan support: `src/main/java/io/micronaut/controlpanel/panels/cache/infinispan`
- Cache operations controller: `src/main/java/io/micronaut/controlpanel/panels/cache/CacheController.java`
- Templates: `src/main/resources/views/cache`
- Tests: `src/test/java/io/micronaut/controlpanel/panels/cache`

## Rules

- Keep provider-specific behavior in provider-specific classes; shared behavior belongs in `AbstractCacheControlPanel`.
- Use `@EachBean` for providers with one panel per cache bean, and existing registrar/loader patterns for dynamic providers.
- Keep provider dependencies optional or test-scoped unless the module intentionally exposes them as API.
- Route cache invalidation through `CacheController` and `SyncCache`; do not bypass the cache abstraction.
- Preserve badge semantics as cache size when available.
- Test provider enablement and unavailable-provider paths, not just happy-path cache operations.

## Verification

- Targeted tests: `./gradlew :micronaut-control-panel-cache:test`
- Some provider tests use container-backed resources; note Docker/Test Resources constraints when they affect local validation.
