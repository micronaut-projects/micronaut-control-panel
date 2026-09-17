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
package io.micronaut.controlpanel.panels.neo4j.model;

import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * Safe connection summary for a Neo4j driver bean.
 *
 * @param beanName driver bean name
 * @param uri masked URI or custom-driver state
 * @param username configured username, when available
 * @param encryption encryption setting, when available
 * @param trustStrategy trust strategy, when available
 */
@ReflectiveAccess
public record Neo4jConnectionInfo(String beanName, String uri, String username, String encryption, String trustStrategy) {
    public boolean hasUsername() {
        return username != null && !username.isBlank();
    }

    public boolean hasEncryption() {
        return encryption != null && !encryption.isBlank();
    }

    public boolean hasTrustStrategy() {
        return trustStrategy != null && !trustStrategy.isBlank();
    }
}
