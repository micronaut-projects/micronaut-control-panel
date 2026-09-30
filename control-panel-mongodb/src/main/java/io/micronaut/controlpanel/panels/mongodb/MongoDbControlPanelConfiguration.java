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
package io.micronaut.controlpanel.panels.mongodb;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;

import java.time.Duration;

/**
 * MongoDB diagnostics panel configuration.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@ConfigurationProperties(MongoDbControlPanelConfiguration.PREFIX)
public final class MongoDbControlPanelConfiguration {

    public static final String PREFIX = ControlPanelConfiguration.PREFIX + ".mongodb";

    private Duration timeout = Duration.ofSeconds(2);
    private int maxDatabases = 25;
    private int maxCollectionsPerDatabase = 100;
    private int maxIndexesPerCollection = 100;
    private final Schema schema = new Schema();
    private final Explain explain = new Explain();

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxDatabases() {
        return maxDatabases;
    }

    public void setMaxDatabases(int maxDatabases) {
        this.maxDatabases = maxDatabases;
    }

    public int getMaxCollectionsPerDatabase() {
        return maxCollectionsPerDatabase;
    }

    public void setMaxCollectionsPerDatabase(int maxCollectionsPerDatabase) {
        this.maxCollectionsPerDatabase = maxCollectionsPerDatabase;
    }

    public int getMaxIndexesPerCollection() {
        return maxIndexesPerCollection;
    }

    public void setMaxIndexesPerCollection(int maxIndexesPerCollection) {
        this.maxIndexesPerCollection = maxIndexesPerCollection;
    }

    public Schema getSchema() {
        return schema;
    }

    public Explain getExplain() {
        return explain;
    }

    /**
     * Schema sampling configuration.
     */
    @ConfigurationProperties("schema")
    public static final class Schema {
        private boolean enabled;
        private int sampleSize = 20;
        private int maxDepth = 4;
        private int maxFields = 200;
        private int maxCollections = 5;
        private boolean redactValues = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSampleSize() {
            return sampleSize;
        }

        public void setSampleSize(int sampleSize) {
            this.sampleSize = sampleSize;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public int getMaxFields() {
            return maxFields;
        }

        public void setMaxFields(int maxFields) {
            this.maxFields = maxFields;
        }

        public int getMaxCollections() {
            return maxCollections;
        }

        public void setMaxCollections(int maxCollections) {
            this.maxCollections = maxCollections;
        }

        public boolean isRedactValues() {
            return redactValues;
        }

        public void setRedactValues(boolean redactValues) {
            this.redactValues = redactValues;
        }
    }

    /**
     * Reserved explain helper configuration.
     */
    @ConfigurationProperties("explain")
    public static final class Explain {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
