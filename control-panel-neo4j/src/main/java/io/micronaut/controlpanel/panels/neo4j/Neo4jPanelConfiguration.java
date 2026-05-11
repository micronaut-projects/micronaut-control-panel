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
package io.micronaut.controlpanel.panels.neo4j;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration for the Neo4j control panel.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@ConfigurationProperties(Neo4jPanelConfiguration.PREFIX)
public class Neo4jPanelConfiguration {

    public static final String PREFIX = "micronaut.control-panel.panels.neo4j";
    public static final int DEFAULT_MAX_LABELS = 100;
    public static final int DEFAULT_MAX_RELATIONSHIP_TYPES = 100;
    public static final int DEFAULT_MAX_PROPERTY_KEYS = 100;

    private int maxLabels = DEFAULT_MAX_LABELS;
    private int maxRelationshipTypes = DEFAULT_MAX_RELATIONSHIP_TYPES;
    private int maxPropertyKeys = DEFAULT_MAX_PROPERTY_KEYS;

    /**
     * Maximum number of labels to read.
     *
     * @return maximum number of labels
     */
    public int getMaxLabels() {
        return maxLabels;
    }

    /**
     * @param maxLabels maximum number of labels
     */
    public void setMaxLabels(int maxLabels) {
        this.maxLabels = bounded(maxLabels);
    }

    /**
     * Maximum number of relationship types to read.
     *
     * @return maximum number of relationship types
     */
    public int getMaxRelationshipTypes() {
        return maxRelationshipTypes;
    }

    /**
     * @param maxRelationshipTypes maximum number of relationship types
     */
    public void setMaxRelationshipTypes(int maxRelationshipTypes) {
        this.maxRelationshipTypes = bounded(maxRelationshipTypes);
    }

    /**
     * Maximum number of property keys to read.
     *
     * @return maximum number of property keys
     */
    public int getMaxPropertyKeys() {
        return maxPropertyKeys;
    }

    /**
     * @param maxPropertyKeys maximum number of property keys
     */
    public void setMaxPropertyKeys(int maxPropertyKeys) {
        this.maxPropertyKeys = bounded(maxPropertyKeys);
    }

    private static int bounded(int value) {
        return Math.max(0, value);
    }
}
