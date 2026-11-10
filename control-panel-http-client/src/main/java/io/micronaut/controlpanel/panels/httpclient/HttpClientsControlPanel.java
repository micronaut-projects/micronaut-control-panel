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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Control panel that lists the HTTP clients of the application: declarative {@code @Client} interfaces, configured
 * services, the instances service discovery reports, and the client filters that apply to each one.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Requires(property = HttpClientsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class HttpClientsControlPanel extends AbstractControlPanel<HttpClientsControlPanel.Body> {

    public static final String NAME = "http-clients";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final HttpClientInspector inspector;

    /**
     * @param inspector the HTTP client inspector
     * @param configuration the panel configuration
     */
    public HttpClientsControlPanel(HttpClientInspector inspector, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.inspector = inspector;
    }

    @Override
    public Body getBody() {
        return new Body(inspector.inspect());
    }

    @Override
    public String getBadge() {
        return StringUtils.EMPTY_STRING;
    }

    /**
     * The HTTP clients. Internal to the HTTP Clients panel views.
     *
     * @param clients the clients, sorted by id
     */
    @ReflectiveAccess
    public record Body(List<HttpClientInfo> clients) {

        /**
         * @return the number of declarative {@code @Client} interfaces
         */
        public long declarativeCount() {
            return clients.stream().mapToLong(client -> client.declarativeTypes().size()).sum();
        }

        /**
         * @return the number of clients without a URL or an instance list
         */
        public long unresolvedCount() {
            return clients.stream().filter(HttpClientInfo::unresolved).count();
        }
    }
}
