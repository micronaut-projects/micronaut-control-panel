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
package io.micronaut.controlpanel.panels.opensearch.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Sanitized OpenSearch connection metadata.
 *
 * @param transport configured transport label
 * @param hosts configured HTTP hosts without credentials
 * @param amazonEndpoint Amazon OpenSearch endpoint without credentials
 * @param signingRegion Amazon request signing region
 * @param hasHosts whether host metadata is present
 * @param hasAmazon whether Amazon OpenSearch metadata is present
 */
@ReflectiveAccess
public record ConnectionContext(String transport,
                                List<String> hosts,
                                String amazonEndpoint,
                                String signingRegion,
                                boolean hasHosts,
                                boolean hasAmazon) {

    public ConnectionContext(String transport, List<String> hosts, String amazonEndpoint, String signingRegion) {
        this(value(transport), hosts == null ? List.of() : List.copyOf(hosts), value(amazonEndpoint), value(signingRegion), false, false);
    }

    public ConnectionContext {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
        hasHosts = !hosts.isEmpty();
        hasAmazon = !value(amazonEndpoint).isBlank() || !value(signingRegion).isBlank();
        transport = value(transport);
        amazonEndpoint = value(amazonEndpoint);
        signingRegion = value(signingRegion);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
