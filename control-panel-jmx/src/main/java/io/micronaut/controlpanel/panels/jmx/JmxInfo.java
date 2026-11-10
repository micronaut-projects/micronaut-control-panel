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
package io.micronaut.controlpanel.panels.jmx;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The MBean server and the endpoint MBeans of the application. Internal to the JMX panel views.
 *
 * @param server the MBean server implementation
 * @param defaultDomain the default domain of the MBean server
 * @param mbeanCount the number of registered MBeans
 * @param domains the domains and their MBean counts, sorted by name
 * @param settings the Micronaut JMX settings
 * @param endpoints the management endpoint MBeans, sorted by object name
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record JmxInfo(
    String server,
    String defaultDomain,
    int mbeanCount,
    List<Domain> domains,
    Settings settings,
    List<Endpoint> endpoints
) {

    /**
     * @return the number of endpoint MBeans that are registered
     */
    public long registeredEndpoints() {
        return endpoints.stream().filter(Endpoint::registered).count();
    }

    /**
     * A JMX domain.
     *
     * @param name the domain name
     * @param mbeans the number of MBeans in the domain
     */
    @ReflectiveAccess
    public record Domain(String name, int mbeans) {
    }

    /**
     * The Micronaut JMX settings.
     *
     * @param domain the default domain of the MBean server Micronaut JMX creates when the platform MBean server is not
     *               available ({@code jmx.domain}), or {@code null} if not set. It does not change the endpoint MBean names
     * @param agentId the agent id used to find the MBean server, if any
     * @param registerEndpoints whether management endpoints are registered as MBeans
     * @param addToFactory whether the MBean server is added to the {@code MBeanServerFactory}
     */
    @ReflectiveAccess
    public record Settings(@Nullable String domain, @Nullable String agentId, boolean registerEndpoints, boolean addToFactory) {
    }

    /**
     * A management endpoint MBean.
     *
     * @param endpoint the endpoint id
     * @param objectName the object name
     * @param registered whether the MBean is registered
     * @param operations the operations, sorted by name
     * @param attributes the attributes, sorted by name
     */
    @ReflectiveAccess
    public record Endpoint(String endpoint, String objectName, boolean registered, List<Operation> operations, List<Attribute> attributes) {
    }

    /**
     * An MBean operation. Operations are listed but never invoked.
     *
     * @param name the operation name
     * @param signature the parameter types
     * @param returnType the return type
     * @param impact the impact: {@code INFO}, {@code ACTION}, {@code ACTION_INFO} or {@code UNKNOWN}
     */
    @ReflectiveAccess
    public record Operation(String name, List<String> signature, String returnType, String impact) {

        /**
         * @return the parameter types, separated by commas
         */
        public String parameters() {
            return String.join(", ", signature);
        }
    }

    /**
     * An MBean attribute. Values are never read.
     *
     * @param name the attribute name
     * @param type the attribute type
     * @param access {@code read-only}, {@code write-only} or {@code read-write}
     */
    @ReflectiveAccess
    public record Attribute(String name, String type, String access) {
    }
}
