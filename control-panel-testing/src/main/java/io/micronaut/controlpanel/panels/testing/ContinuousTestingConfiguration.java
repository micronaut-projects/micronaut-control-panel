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
package io.micronaut.controlpanel.panels.testing;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Configures how the Continuous Testing panel finds the continuous testing process of the project and its report.
 *
 * @param projectDirectory The project directory the report directories resolve against. Defaults to the working
 *                         directory of the application, the project directory in development mode.
 * @param reportDirectory The directory continuous testing writes its HTML report to, the value of
 *                        {@code micronaut.dev.test.html-report} of the continuous testing process. Defaults to
 *                        {@code micronaut.dev.test.html-report} when the application has it, then to the directories
 *                        the build plugins use.
 * @param reportUrl The address the LiveReload server of the continuous testing process serves the report at. Defaults
 *                  to the address the report names, then to {@code http://localhost:<live-reload-port>} and
 *                  {@code micronaut.dev.test.html-report-path}, by default {@code /tests/}.
 * @param liveReloadPort The port of the LiveReload server of the continuous testing process. Default: 35729.
 * @param probeTimeout How long to wait for the LiveReload server when checking whether it serves the report.
 *                     Default: 500ms.
 * @param maxFailures How many failed tests the panel lists. Default: 50.
 * @since 2.3.0
 */
@ConfigurationProperties(ContinuousTestingConfiguration.PREFIX)
public record ContinuousTestingConfiguration(
    @Nullable
    String projectDirectory,
    @Nullable
    String reportDirectory,
    @Nullable
    String reportUrl,
    @Bindable(defaultValue = "35729")
    int liveReloadPort,
    @Bindable(defaultValue = "500ms")
    Duration probeTimeout,
    @Bindable(defaultValue = "50")
    int maxFailures
) {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = ControlPanelConfiguration.PREFIX + "." + ContinuousTestingControlPanel.NAME;
}
