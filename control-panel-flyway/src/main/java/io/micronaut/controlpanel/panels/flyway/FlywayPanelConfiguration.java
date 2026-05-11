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
package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.bind.annotation.Bindable;
import org.flywaydb.core.Flyway;

/**
 * Display configuration for the Flyway control panel.
 */
@ConfigurationProperties(FlywayPanelConfiguration.PREFIX)
@Requires(classes = Flyway.class)
public interface FlywayPanelConfiguration {

    /**
     * Flyway panel configuration prefix.
     */
    String PREFIX = ControlPanelConfiguration.PREFIX + ".flyway";
    /**
     * Default checksum visibility.
     */
    String DEFAULT_SHOW_CHECKSUMS = "true";
    /**
     * Default installed-by visibility.
     */
    String DEFAULT_SHOW_INSTALLED_BY = "true";

    /**
     * Whether to display migration checksums when Flyway reports them.
     *
     * @return true if checksum values should be displayed
     */
    @Bindable(defaultValue = DEFAULT_SHOW_CHECKSUMS)
    boolean isShowChecksums();

    /**
     * Whether to display the installed-by user when Flyway reports it.
     *
     * @return true if installed-by values should be displayed
     */
    @Bindable(defaultValue = DEFAULT_SHOW_INSTALLED_BY)
    boolean isShowInstalledBy();
}
