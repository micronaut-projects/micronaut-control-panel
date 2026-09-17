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
package io.micronaut.controlpanel.panels.opensearch;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the OpenSearch diagnostics control panel.
 */
@ConfigurationProperties(OpenSearchDiagnosticsConfiguration.PREFIX)
public interface OpenSearchDiagnosticsConfiguration {

    String PREFIX = "micronaut.control-panel.panels.opensearch";
    String DEFAULT_MAX_INDICES = "25";
    String DEFAULT_MAX_MAPPING_FIELDS = "20";

    /**
     * Maximum number of indices rendered in the panel.
     *
     * @return the maximum index rows
     */
    @Bindable(defaultValue = DEFAULT_MAX_INDICES)
    int getMaxIndices();

    /**
     * Maximum number of mapping fields rendered for each index.
     *
     * @return the maximum mapping fields per index
     */
    @Bindable(defaultValue = DEFAULT_MAX_MAPPING_FIELDS)
    int getMaxMappingFields();
}
