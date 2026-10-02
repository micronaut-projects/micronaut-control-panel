/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.toml;

import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Read-only diagnostics for TOML-backed configuration property sources.
 */
@Singleton
public class TomlConfigurationControlPanel extends AbstractControlPanel<TomlConfigurationControlPanel.Body> {

    /**
     * Panel name.
     */
    public static final String NAME = "toml";
    /**
     * Configuration property that enables or disables this panel.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    /**
     * Configuration category used in the Control Panel navigation.
     */
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("configuration", "Configuration", "fa-sliders", 10);

    private final Environment environment;
    private final TomlPanelConfiguration tomlConfiguration;
    private final TomlPropertySourceAnalyzer analyzer;

    /**
     * Creates the TOML configuration control panel.
     *
     * @param environment The Micronaut environment
     * @param tomlConfiguration TOML panel configuration
     * @param analyzer TOML property-source analyzer
     * @param configuration panel configuration
     */
    public TomlConfigurationControlPanel(Environment environment,
                                         TomlPanelConfiguration tomlConfiguration,
                                         TomlPropertySourceAnalyzer analyzer,
                                         @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.environment = environment;
        this.tomlConfiguration = tomlConfiguration;
        this.analyzer = analyzer;
    }

    @Override
    public Body getBody() {
        return analyzer.analyze(environment, tomlConfiguration);
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getBadge() {
        Summary summary = getBody().summary();
        return summary.sourceCount() + " sources / " + summary.uniqueKeys() + " keys";
    }

    @Override
    public String getDetailLinkName() {
        return "Inspect TOML keys";
    }

    /**
     * TOML diagnostics payload.
     *
     * @param activeEnvironments active Micronaut environments
     * @param summary summary counts
     * @param sources TOML property sources
     * @param keys TOML key rows
     * @param valuesVisible whether effective non-sensitive values may be displayed
     * @param overriddenValuesVisible whether overridden non-sensitive values may be displayed
     * @param hasTomlSources whether any TOML source was detected
     */
    @ReflectiveAccess
    public record Body(Set<String> activeEnvironments,
                       Summary summary,
                       List<TomlSource> sources,
                       List<TomlKeyEntry> keys,
                       boolean valuesVisible,
                       boolean overriddenValuesVisible,
                       boolean hasTomlSources) {
    }

    /**
     * Summary count model.
     *
     * @param sourceCount number of TOML property sources
     * @param totalRows number of TOML source/key rows
     * @param uniqueKeys number of unique TOML keys
     * @param effectiveRows effective TOML rows
     * @param overriddenRows overridden TOML rows
     * @param unknownRows rows where status could not be proven
     */
    @ReflectiveAccess
    public record Summary(int sourceCount,
                          int totalRows,
                          int uniqueKeys,
                          int effectiveRows,
                          int overriddenRows,
                          int unknownRows) {
    }

    /**
     * TOML source row.
     *
     * @param name source name
     * @param origin source origin
     * @param order source order
     * @param precedence runtime precedence position
     * @param keyCount number of keys in the source
     * @param effectiveCount effective key count
     * @param overriddenCount overridden key count
     * @param unknownCount unknown key count
     */
    @ReflectiveAccess
    public record TomlSource(String name,
                             String origin,
                             int order,
                             int precedence,
                             int keyCount,
                             int effectiveCount,
                             int overriddenCount,
                             int unknownCount) {
    }

    /**
     * TOML key row.
     *
     * @param key flattened key
     * @param type value type
     * @param displayValue rendered value
     * @param sourceName source name
     * @param sourceOrigin source origin
     * @param status TOML key status
     * @param overridingSource overriding source name
     * @param statusLabel human-readable status
     */
    @ReflectiveAccess
    public record TomlKeyEntry(String key,
                               String type,
                               String displayValue,
                               String sourceName,
                               String sourceOrigin,
                               TomlPropertySourceAnalyzer.TomlKeyStatus status,
                               String overridingSource,
                               String statusLabel) {
    }
}
