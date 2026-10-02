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
package io.micronaut.controlpanel.panels.elasticsearch;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;

import java.time.Duration;

/**
 * Elasticsearch diagnostics panel probe limits.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@ConfigurationProperties(ElasticsearchControlPanelConfiguration.PREFIX)
public class ElasticsearchControlPanelConfiguration {

    public static final String PREFIX = ControlPanelConfiguration.PREFIX + "." + ElasticsearchControlPanel.NAME;

    private static final int DEFAULT_MAX_INDICES = 50;
    private static final int DEFAULT_MAX_ALIASES_PER_INDEX = 10;
    private static final int DEFAULT_MAX_MAPPING_FIELDS = 50;
    private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(5);

    private int maxIndices = DEFAULT_MAX_INDICES;
    private int maxAliasesPerIndex = DEFAULT_MAX_ALIASES_PER_INDEX;
    private int maxMappingFields = DEFAULT_MAX_MAPPING_FIELDS;
    private Duration probeTimeout = DEFAULT_PROBE_TIMEOUT;

    /**
     * Maximum number of indices to render in the panel. Default value: {@value #DEFAULT_MAX_INDICES}.
     *
     * @return the maximum index display count
     */
    public int getMaxIndices() {
        return maxIndices;
    }

    /**
     * Sets the maximum number of indices to render in the panel.
     *
     * @param maxIndices the maximum number of indices
     */
    public void setMaxIndices(int maxIndices) {
        this.maxIndices = positiveOrDefault(maxIndices, DEFAULT_MAX_INDICES);
    }

    /**
     * Maximum aliases shown for each index. Default value: {@value #DEFAULT_MAX_ALIASES_PER_INDEX}.
     *
     * @return the maximum aliases to display for each index
     */
    public int getMaxAliasesPerIndex() {
        return maxAliasesPerIndex;
    }

    /**
     * Sets the maximum aliases shown for each index.
     *
     * @param maxAliasesPerIndex the maximum aliases per index
     */
    public void setMaxAliasesPerIndex(int maxAliasesPerIndex) {
        this.maxAliasesPerIndex = positiveOrDefault(maxAliasesPerIndex, DEFAULT_MAX_ALIASES_PER_INDEX);
    }

    /**
     * Maximum mapping fields shown across the panel. Default value: {@value #DEFAULT_MAX_MAPPING_FIELDS}.
     *
     * @return the maximum mapping fields to display
     */
    public int getMaxMappingFields() {
        return maxMappingFields;
    }

    /**
     * Sets the maximum mapping fields shown across the panel.
     *
     * @param maxMappingFields the maximum mapping fields
     */
    public void setMaxMappingFields(int maxMappingFields) {
        this.maxMappingFields = positiveOrDefault(maxMappingFields, DEFAULT_MAX_MAPPING_FIELDS);
    }

    /**
     * Timeout for each Elasticsearch diagnostics probe. Default value: 5 seconds.
     *
     * @return the diagnostics probe timeout
     */
    public Duration getProbeTimeout() {
        return probeTimeout;
    }

    /**
     * Sets the timeout for each Elasticsearch diagnostics probe.
     *
     * @param probeTimeout the probe timeout
     */
    public void setProbeTimeout(Duration probeTimeout) {
        this.probeTimeout = probeTimeout == null || probeTimeout.isNegative() || probeTimeout.isZero() ? DEFAULT_PROBE_TIMEOUT : probeTimeout;
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
