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
package io.micronaut.controlpanel.panels.oraclecloud;

import io.micronaut.core.annotation.Internal;

import java.util.Locale;
import java.util.Set;

/**
 * Classifies OCI configuration keys so diagnostics can expose presence without exposing values.
 */
@Internal
final class OciDiagnosticsRedactor {

    static final String HIDDEN = "hidden by design";

    private static final Set<String> SECRET_KEY_PARTS = Set.of(
        "fingerprint",
        "passphrase",
        "password",
        "private-key",
        "privatekey",
        "secret",
        "security-token",
        "session-token",
        "token",
        "user-id",
        "userid",
        "tenant-id",
        "tenancy-id",
        "tenantid",
        "tenancyid",
        "ocid",
        "key-store",
        "keystore",
        "trust-store",
        "truststore",
        "certificate",
        "cert",
        "proxy-username",
        "proxy-password",
        "profile",
        "path"
    );

    private OciDiagnosticsRedactor() {
    }

    static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace('_', '-');
        for (String part : SECRET_KEY_PARTS) {
            if (normalized.contains(part)) {
                return true;
            }
        }
        return false;
    }

    static String safePropertyCategory(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (isSensitiveKey(normalized)) {
            return HIDDEN;
        }
        if (normalized.contains("timeout")) {
            return "timeout";
        }
        if (normalized.contains("retry") || normalized.contains("circuit-breaker") || normalized.contains("termination-strategy") || normalized.contains("delay-strategy")) {
            return "retry";
        }
        if (normalized.contains("proxy")) {
            return "proxy";
        }
        if (normalized.contains("ssl") || normalized.contains("tls")) {
            return "ssl";
        }
        if (normalized.contains("log")) {
            return "logging";
        }
        if (normalized.contains("http2") || normalized.contains("http-2")) {
            return "http2";
        }
        if (normalized.contains("connection-pool") || normalized.contains("connectionpool") || normalized.contains("pool")) {
            return "connection pool";
        }
        if (normalized.contains("endpoint")) {
            return "endpoint";
        }
        if (normalized.contains("region")) {
            return "region";
        }
        return "configured";
    }
}
