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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

/**
 * The Pulsar client configuration. Internal to the Pulsar panel views.
 *
 * @param serviceUrl the service URL, without user info or query string
 * @param authentication the authentication method, such as {@code token} or {@code none}, without the secret itself
 * @param tls whether the service URL uses TLS
 * @param defaultTenant the default tenant, if configured
 * @param proxyUrl the proxy URL, without user info or query string, if configured
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record PulsarClientInfo(
    String serviceUrl,
    String authentication,
    boolean tls,
    @Nullable String defaultTenant,
    @Nullable String proxyUrl
) {
}
