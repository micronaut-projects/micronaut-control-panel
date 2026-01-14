# MODULE KNOWLEDGE BASE: control-panel-cache

Generated: 2026-01-14
Commit: d5ed20d (example; update with actual)
Branch: 2.0.x

## OVERVIEW
Micronaut Control Panel Cache Module: Provides control panels for managing various cache providers (Caffeine, Ehcache,
Hazelcast, Infinispan) in Micronaut applications. Includes UI components for viewing cache stats, contents, and performing
operations like invalidation. Java-based with JUnit tests.

## STRUCTURE
```
control-panel-cache/
├── src/main/java/io/micronaut/controlpanel/panels/cache/       # Core cache panel implementations
│   ├── hazelcast/                                              # Hazelcast-specific
│   └── infinispan/                                             # Infinispan-specific
├── src/main/resources/views/cache/                             # Handlebars templates for cache UI
├── src/test/java/io/micronaut/controlpanel/panels/cache/       # Unit tests for panels
└── src/test/resources/                                         # Test configs (e.g., application-infinispan.yml)
```

## WHERE TO LOOK
- Core abstractions: src/main/java/io/micronaut/controlpanel/panels/cache/AbstractCacheControlPanel.java
- Provider panels: src/main/java/io/micronaut/controlpanel/panels/cache/{Caffeine,Ehcache}CacheControlPanel.java
- Dynamic loaders/registrars: src/main/java/io/micronaut/controlpanel/panels/cache/hazelcast/HazelcastControlPanelRegistrar.java and infinispan/InfinispanControlPanelLoader.java
- Controller for ops: src/main/java/io/micronaut/controlpanel/panels/cache/CacheController.java
- UI templates: src/main/resources/views/cache/{body,detail}.hbs
- Tests: src/test/java/io/micronaut/controlpanel/panels/cache/*Test.java
- Test resources: src/test/resources/application-infinispan.yml (configures Infinispan for tests)

## CODE MAP
- AbstractCacheControlPanel (abstract class): Base for all cache panels; handles common logic like displaying cache info, size (as badge), contents as map, and category/icon.
- CacheController (class): HTTP controller with routes for cache operations (DELETE /cache-control-panel-controller/{cacheName} to invalidate all, /cache-control-panel-controller/{cacheName}/{key} to invalidate specific).
- CaffeineCacheControlPanel (class): Panel for Caffeine; uses @EachBean(DefaultSyncCache.class).
- EhcacheControlPanel (class): Panel for Ehcache; uses @EachBean(EhcacheSyncCache.class).
- HazelcastSyncCacheControlPanel (class): Panel for Hazelcast; created dynamically via registrar.
- InfinispanSyncCacheControlPanel (class): Panel for Infinispan; created dynamically via loader.
- HazelcastControlPanelRegistrar (class): Registers Hazelcast panels on cache creation; requires HazelcastCacheManager and HazelcastInstance beans.
- InfinispanControlPanelLoader (class): Loads Infinispan panels at startup; requires ControlPanelConfiguration and 'infinispan.enabled' property.

## CONVENTIONS
- Per-provider patterns: Use @EachBean for static providers (Caffeine, Ehcache); dynamic registrars/loaders for runtime-created caches (Hazelcast, Infinispan).
- Bean conditions: Package-enabled via 'controlpanel.cache.enabled' (default true); per-provider via @Requires (e.g., beansPresent for Hazelcast, property for Infinispan).
- Cache ops: Panels provide size (badge), contents map; controller handles invalidate/invalidateAll via SyncCache methods.
- Badges: Cache size shown as badge in UI.
- Routes: DELETE endpoints in CacheController for invalidation; panels integrated via ControlPanelRepository.
- Follow Micronaut conventions: Annotation-driven, GraalVM-friendly, minimal deps.

## TESTS (JUnit)
- AbstractCacheControlPanelTest: Common tests for panel init, size, contents, bean presence.
- Per-provider tests (e.g., CaffeineCacheControlPanelTest): Extend abstract; verify provider-specific ops like put/invalidate.
- Use test resources like application-infinispan.yml for configuring providers in tests (e.g., enables Infinispan with test container settings).

## ANTI-PATTERNS (THIS MODULE)
- Avoid mixing provider logic; keep implementations isolated.
- Don't add direct deps on providers (use compileOnly); rely on runtime presence.
- No custom cache ops outside controller; use SyncCache interface.
- Avoid static panel creation for dynamic caches (use loaders/registrars).

## UNIQUE STYLES
- Dynamic panel creation for Hazelcast/Infinispan to handle runtime cache instantiation.
- Custom content extraction overriding asMap() for providers without direct map access.
- Integration with ControlPanelConfiguration for enabling/disabling.

## COMMANDS
- Build & test: ./gradlew :control-panel-cache:test
- Spotless check/apply: ./gradlew spotlessCheck | ./gradlew spotlessApply

## NOTES
- Module depends on micronaut-cache-core; optional compileOnly on specific providers.
- Test resources use containers (e.g., Infinispan server) for realistic testing.
- Panels categorized under 'Cache' with icons; badges show cache size.
