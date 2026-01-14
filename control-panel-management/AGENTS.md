# MODULE KNOWLEDGE BASE: control-panel-management

Generated: 2026-01-14
Commit: d5ed20d (aligned with root)
Branch: 2.0.x

## OVERVIEW
Module providing control panels integrating with Micronaut management endpoints for environment, health, loggers, and beans.

## WHERE TO LOOK
- Core panel implementations: src/main/java/io/micronaut/controlpanel/panels/management
- Beans-specific panels: src/main/java/io/micronaut/controlpanel/panels/management/beans
- Filters for endpoints: src/main/java/io/micronaut/controlpanel/panels/management (e.g., AllPlainEnvironmentEndpointFilter.java)
- Spock tests: src/test/groovy/io/micronaut/controlpanel/panels/management (e.g., EnvironmentControlPanelSpec.groovy)
- Build configuration: build.gradle.kts (dependencies on core and management modules)
- Resources: src/main/resources/micronaut-control-panel (properties) and views (templates for panel rendering)

## CODE MAP
- EnvironmentControlPanel (class): panels/management/EnvironmentControlPanel.java — Displays environment properties via EnvironmentEndpoint
- HealthControlPanel (class): panels/management/HealthControlPanel.java — Shows application health status using HealthEndpoint
- LoggersControlPanel (class): panels/management/LoggersControlPanel.java — Manages logger configurations with ManagedLoggingSystem
- BeansControlPanel (class): panels/management/beans/BeansControlPanel.java — Lists active beans grouped by package from BeanContext
- DisabledBeansControlPanel (class): panels/management/beans/DisabledBeansControlPanel.java — Displays disabled beans from BeanContext
- AllPlainEnvironmentEndpointFilter (class): panels/management/AllPlainEnvironmentEndpointFilter.java — Filter to show unmasked environment values (no Routes-specific filter; use for general endpoint filtering)

## CONVENTIONS
- Panels extend AbstractControlPanel and use @Requires for conditional enabling based on endpoint beans and config properties
- Use @Singleton and @Refreshable where appropriate for panel lifecycle
- Integrate directly with Micronaut endpoints (e.g., EnvironmentEndpoint, HealthEndpoint) for data retrieval
- Tests exclusively in Spock (Groovy) under src/test/groovy with behavior-driven specs
- Configuration via properties like micronaut.control-panel.<panel-name>.enabled
- Panels compute badges (e.g., property count) and bodies from endpoint data

## ANTI-PATTERNS (THIS MODULE)
- Avoid direct reflection; rely on Micronaut's annotation-driven injection
- Do not mix panel logic across domains (e.g., keep beans separate from loggers)
- No custom build logic in build.gradle.kts; use root conventions
- Steer clear of hardcoded values; use configurable filters like AllPlainEnvironmentEndpointFilter
- Do not add dependencies without BOM alignment; stick to management and core modules

## NOTES
- Module requires Micronaut management features enabled
- Focus on development-time visibility; not for production use
- Tests validate panel enabling/disabling and data accuracy with Spock
