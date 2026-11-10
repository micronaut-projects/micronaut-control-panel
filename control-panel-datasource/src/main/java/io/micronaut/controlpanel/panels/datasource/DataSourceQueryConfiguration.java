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
package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the datasource SQL console.
 *
 * @since 2.2.1
 */
@ConfigurationProperties(DataSourceQueryConfiguration.PREFIX)
public interface DataSourceQueryConfiguration {

    String PREFIX = "micronaut.control-panel.datasource";
    String PROPERTY_ALLOW_WRITES = PREFIX + ".allow-writes";
    String DEFAULT_ALLOW_WRITES = "false";

    /**
     * Whether the datasource SQL console may run data-modifying statements (anything that is not a
     * {@code SELECT}/{@code WITH} query). Disabled by default so the console is read-only; enable it
     * only in a trusted development environment. Default: {@value #DEFAULT_ALLOW_WRITES}.
     *
     * @return whether write statements are permitted
     */
    @Bindable(defaultValue = DEFAULT_ALLOW_WRITES)
    boolean isAllowWrites();
}
