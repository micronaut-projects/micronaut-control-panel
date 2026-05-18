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
package io.micronaut.controlpanel.panels.kafka;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Optional Kafka ecosystem integration endpoints for the Kafka cluster panel.
 *
 * @param schemaRegistry The optional Schema Registry endpoint.
 * @param connect The optional Kafka Connect endpoint.
 * @param ksqldb The optional ksqlDB endpoint.
 */
@Internal
@ConfigurationProperties(KafkaIntegrationConfiguration.PREFIX)
record KafkaIntegrationConfiguration(
    @Nullable
    Endpoint schemaRegistry,
    @Nullable
    Endpoint connect,
    @Nullable
    Endpoint ksqldb
) {

    static final String PREFIX = ControlPanelConfiguration.PREFIX + ".kafka.integrations";
    static final String PROPERTY_SCHEMA_REGISTRY_URL = PREFIX + ".schema-registry.url";
    static final String PROPERTY_CONNECT_URL = PREFIX + ".connect.url";
    static final String PROPERTY_KSQLDB_URL = PREFIX + ".ksqldb.url";

    KafkaIntegrationConfiguration() {
        this(new Endpoint(null), new Endpoint(null), new Endpoint(null));
    }

    KafkaIntegrationConfiguration {
        if (schemaRegistry == null) {
            schemaRegistry = new Endpoint(null);
        }
        if (connect == null) {
            connect = new Endpoint(null);
        }
        if (ksqldb == null) {
            ksqldb = new Endpoint(null);
        }
    }

    @Internal
    record Endpoint(@Nullable String url) {

        boolean isConfigured() {
            return url != null && !url.isBlank();
        }
    }
}
