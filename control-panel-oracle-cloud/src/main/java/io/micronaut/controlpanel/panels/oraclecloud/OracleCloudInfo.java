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

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the application connects to Oracle Cloud. Internal to the Oracle Cloud panel views.
 *
 * @param authentication the authentication provider the SDK clients use, or {@code null} when none is available
 * @param candidates the authentication providers whose requirements are met, when more than one is
 * @param profile the profile of the OCI configuration file, when the configuration file is used
 * @param customConfigPath whether the OCI configuration file is read from a custom path
 * @param region the region, when the authentication provider has been created and reports one
 * @param httpProvider the HTTP client the SDK clients use
 * @param clients the SDK clients
 * @param clientConfigurations the client configuration under {@code oci.client} and {@code oci.clients.<service>}
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record OracleCloudInfo(
    @Nullable Authentication authentication,
    List<Authentication> candidates,
    @Nullable String profile,
    boolean customConfigPath,
    @Nullable String region,
    String httpProvider,
    List<Client> clients,
    List<ClientConfiguration> clientConfigurations
) {

    /**
     * @return the number of SDK clients that have been created
     */
    public long createdCount() {
        return clients.stream().filter(Client::created).count();
    }

    /**
     * An authentication provider.
     *
     * @param label what the provider authenticates with
     * @param type the provider class
     * @param created whether the provider has been created
     */
    @ReflectiveAccess
    public record Authentication(String label, String type, boolean created) {
    }

    /**
     * An OCI SDK client.
     *
     * @param service the service name
     * @param type the client class
     * @param async whether it is an asynchronous client
     * @param created whether the client has been created
     * @param endpoint the endpoint, when the client has been created
     */
    @ReflectiveAccess
    public record Client(String service, String type, boolean async, boolean created, @Nullable String endpoint) {
    }

    /**
     * Client configuration.
     *
     * @param name {@code oci.client} for the shared configuration, or {@code oci.clients.<service>}
     * @param connectTimeout the connect timeout, if configured
     * @param readTimeout the read timeout, if configured
     */
    @ReflectiveAccess
    public record ClientConfiguration(String name, @Nullable String connectTimeout, @Nullable String readTimeout) {
    }
}
