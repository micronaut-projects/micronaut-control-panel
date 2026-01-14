# MODULE KNOWLEDGE BASE: control-panel-core

Generated: 2026-01-14T12:56:00+01:00
Commit: d5ed20d (root)
Branch: 2.0.x (root)

## OVERVIEW
Core abstractions, interfaces, and base implementations for defining and managing control panels in Micronaut applications.

## WHERE TO LOOK
- Core interfaces and abstracts: src/main/java/io/micronaut/controlpanel/core
- Configuration classes: src/main/java/io/micronaut/controlpanel/core/config
- Built-in panels: src/main/java/io/micronaut/controlpanel/core/panels (e.g., RoutesControlPanel.java, TestResourcesControlPanel.java)
- Utilities: src/main/java/io/micronaut/controlpanel/util/ControlPanelUtils.java
- Handlebars templates: src/main/resources/views (e.g., routes/body.hbs, test-resources/body.hbs)
- Tests: src/test/groovy/io/micronaut/controlpanel/core (Spock specs)
- Test configs: src/test/resources/logback.xml
- Native image config: src/main/resources/META-INF/native-image/.../reflect-config.json

## CODE MAP
- ControlPanel (interface): core/ControlPanel.java — Defines panel contract with body, views, category, badge; nested Category and View records.
- AbstractControlPanel (class): core/AbstractControlPanel.java — Base for panels, delegates to ControlPanelConfiguration for name, title, icon, order, enabled.
- ConfigurableControlPanel (interface): core/ConfigurableControlPanel.java — Provides title, icon, enabled, order for configurable panels.
- ControlPanelRepository (interface): core/ControlPanelRepository.java — Retrieves panels by name, category; counts per category.
- DefaultControlPanelRepository (class): core/DefaultControlPanelRepository.java — Implements repository using BeanContext and ControlPanelLoader.
- ControlPanelLoader (interface): core/ControlPanelLoader.java — For dynamic panel loading beyond static beans.
- ControlPanelConfiguration (class): core/config/ControlPanelConfiguration.java — @EachProperty config for individual panels (prefix: micronaut.control-panel.panels).
- ControlPanelModuleConfiguration (interface): core/config/ControlPanelModuleConfiguration.java — Module-level config (enabled, path, allowed environments).
- ControlPanelEnabledCondition (class): core/config/ControlPanelEnabledCondition.java — @Requires condition based on module config and environment.
- ControlPanelUrlLogger (class): core/ControlPanelUrlLogger.java — Logs control panel URL on startup if enabled.

## CONVENTIONS
- Panels implement ControlPanel or extend AbstractControlPanel; use @Requires for conditional loading.
- Categories via ControlPanel.Category record; order panels with getOrder().
- Dynamic loading via ControlPanelLoader beans.
- Testing: Spock (Groovy) with data-driven specs; module-specific logback.xml for test logging.
- Config: @ConfigurationProperties for module, @EachProperty for per-panel; defaults in ControlPanelModuleConfiguration.
- Native: reflect-config.json for GraalVM compatibility on key classes.

## ANTI-PATTERNS (THIS MODULE)
- Avoid direct instantiation of panels; rely on repository and BeanContext discovery.
- Don't hardcode categories or orders; use records and config delegation.
- No ad-hoc config properties; stick to micronaut.control-panel.panels prefix.
- Skip Java tests; use Spock for consistency with data tables for edge cases.
