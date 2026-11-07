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
package io.micronaut.controlpanel.panels.httpclient;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An HTTP client of the application. Internal to the HTTP Clients panel views.
 *
 * @param id the client or service id; {@value HttpClientInspector#SAME_SERVER} for clients that call this server
 * @param declarativeTypes the {@code @Client} interfaces that use this client
 * @param configured whether the client is configured under {@code micronaut.http.services}
 * @param urls the configured URLs, without user info or query strings
 * @param path the base path, if any
 * @param connectTimeout the connect timeout, if configured
 * @param readTimeout the read timeout, if configured
 * @param healthCheck whether the configured URLs are health checked
 * @param instances the instances service discovery reports, or {@code null} when no instance list exists for the id
 * @param filters the client filters that apply to this client
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record HttpClientInfo(
    String id,
    List<String> declarativeTypes,
    boolean configured,
    List<String> urls,
    @Nullable String path,
    @Nullable String connectTimeout,
    @Nullable String readTimeout,
    boolean healthCheck,
    @Nullable List<Instance> instances,
    List<String> filters
) {

    /**
     * @return whether service discovery is used for this client: it has an instance list and no fixed URLs
     */
    public boolean discovered() {
        return instances != null && urls.isEmpty();
    }

    /**
     * @return whether this client has no URL, no instance list, and is not a same-server client
     */
    public boolean unresolved() {
        return urls.isEmpty() && instances == null && !id.equals(HttpClientInspector.SAME_SERVER);
    }

    /**
     * A service instance.
     *
     * @param uri the instance URI, without user info or query string
     * @param health the instance health status
     */
    @ReflectiveAccess
    public record Instance(String uri, String health) {

        /**
         * @return whether the instance reports itself as up
         */
        public boolean up() {
            return "UP".equalsIgnoreCase(health);
        }
    }
}
