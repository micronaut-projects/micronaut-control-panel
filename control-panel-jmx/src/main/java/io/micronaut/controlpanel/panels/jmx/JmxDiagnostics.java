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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Read-only JMX diagnostics rendered by the JMX Control Panel.
 *
 * @param serverAvailable whether an MBean server bean is available
 * @param serverClassName MBean server implementation class name
 * @param defaultDomain server default domain
 * @param endpointDomain Micronaut endpoint MBean domain
 * @param totalMBeanCount total MBean count reported by the server
 * @param domainCount number of readable domains
 * @param endpointMBeanCount number of Micronaut endpoint MBeans
 * @param metadataFailureCount number of endpoint MBeans with metadata warnings
 * @param settings visible JMX settings
 * @param domainSummaries MBean counts grouped by domain
 * @param endpointMBeans Micronaut endpoint MBean metadata
 * @param warnings panel-level warnings
 * @param hasDomainSummaries whether domain summaries are available
 * @param hasEndpointMBeans whether endpoint MBeans are available
 * @param hasWarnings whether panel warnings are available
 */
@Internal
@ReflectiveAccess
public record JmxDiagnostics(boolean serverAvailable,
                             String serverClassName,
                             String defaultDomain,
                             String endpointDomain,
                             int totalMBeanCount,
                             int domainCount,
                             int endpointMBeanCount,
                             int metadataFailureCount,
                             @Nullable JmxSettings settings,
                             @Nullable List<DomainSummary> domainSummaries,
                             @Nullable List<EndpointMBean> endpointMBeans,
                             @Nullable List<Warning> warnings,
                             boolean hasDomainSummaries,
                             boolean hasEndpointMBeans,
                             boolean hasWarnings) {

    public JmxDiagnostics {
        settings = settings == null ? JmxSettings.unavailable() : settings;
        domainSummaries = domainSummaries == null ? List.of() : List.copyOf(domainSummaries);
        endpointMBeans = endpointMBeans == null ? List.of() : List.copyOf(endpointMBeans);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        hasDomainSummaries = !domainSummaries.isEmpty();
        hasEndpointMBeans = !endpointMBeans.isEmpty();
        hasWarnings = !warnings.isEmpty();
    }

    static JmxDiagnostics unavailable(JmxSettings settings, List<Warning> warnings) {
        return new JmxDiagnostics(false, "Unavailable", "Unavailable", JmxDiagnosticsService.MICRONAUT_ENDPOINT_DOMAIN, 0, 0, 0, 0, settings, List.of(), List.of(), warnings, false, false, false);
    }

    /**
     * JMX configuration values visible to the panel.
     *
     * @param configurationClassPresent whether Micronaut JMX configuration is on the classpath
     * @param hasExplicitProperties whether any JMX setting is explicitly configured
     * @param agentId configured agent id setting
     * @param domain configured domain setting
     * @param addToFactory configured add-to-factory setting
     * @param ignoreAgentNotFound configured ignore-agent-not-found setting
     * @param registerEndpoints configured register-endpoints setting
     * @param registerEndpointsDisabled whether endpoint registration is disabled
     */
    @ReflectiveAccess
    public record JmxSettings(boolean configurationClassPresent,
                              boolean hasExplicitProperties,
                              @Nullable Setting agentId,
                              @Nullable Setting domain,
                              @Nullable Setting addToFactory,
                              @Nullable Setting ignoreAgentNotFound,
                              @Nullable Setting registerEndpoints,
                              boolean registerEndpointsDisabled) {

        public JmxSettings {
            agentId = agentId == null ? Setting.notConfigured("jmx.agent-id") : agentId;
            domain = domain == null ? Setting.notConfigured("jmx.domain") : domain;
            addToFactory = addToFactory == null ? Setting.defaulted("jmx.add-to-factory", "true") : addToFactory;
            ignoreAgentNotFound = ignoreAgentNotFound == null ? Setting.defaulted("jmx.ignore-agent-not-found", "false") : ignoreAgentNotFound;
            registerEndpoints = registerEndpoints == null ? Setting.defaulted("jmx.register-endpoints", "true") : registerEndpoints;
            registerEndpointsDisabled = "false".equals(registerEndpoints.value());
        }

        static JmxSettings unavailable() {
            return new JmxSettings(false, false, null, null, null, null, null, false);
        }
    }

    /**
     * One configuration setting.
     *
     * @param name property name
     * @param value rendered property value
     * @param source rendered value source
     * @param configured whether the value was explicitly configured
     */
    @ReflectiveAccess
    public record Setting(String name, String value, String source, boolean configured) {
        static Setting configured(String name, String value) {
            return new Setting(name, value, "configured", true);
        }

        static Setting defaulted(String name, String value) {
            return new Setting(name, value, "default", false);
        }

        static Setting notConfigured(String name) {
            return new Setting(name, "not configured", "not configured", false);
        }
    }

    /**
     * Count of MBeans grouped by domain.
     *
     * @param domain JMX domain
     * @param mBeanCount number of MBeans in the domain
     */
    @ReflectiveAccess
    public record DomainSummary(String domain, int mBeanCount) {
    }

    /**
     * One Micronaut endpoint MBean and the metadata that could be read safely.
     *
     * @param objectName redacted ObjectName
     * @param endpointType endpoint type property
     * @param registered whether the MBean is currently registered
     * @param status rendered registration or metadata status
     * @param warning row-level metadata warning
     * @param operationCount number of operations
     * @param infoImpactCount number of INFO operations
     * @param actionImpactCount number of ACTION operations
     * @param actionInfoImpactCount number of ACTION_INFO operations
     * @param unknownImpactCount number of operations with unknown impact
     * @param attributeCount number of attributes
     * @param operations operation metadata
     * @param attributes attribute metadata
     * @param hasWarning whether the row has a warning
     * @param hasOperations whether operations are available
     * @param hasAttributes whether attributes are available
     */
    @ReflectiveAccess
    public record EndpointMBean(String objectName,
                                String endpointType,
                                boolean registered,
                                String status,
                                String warning,
                                int operationCount,
                                int infoImpactCount,
                                int actionImpactCount,
                                int actionInfoImpactCount,
                                int unknownImpactCount,
                                int attributeCount,
                                List<Operation> operations,
                                List<Attribute> attributes,
                                boolean hasWarning,
                                boolean hasOperations,
                                boolean hasAttributes) {

        public EndpointMBean {
            operations = operations == null ? List.of() : List.copyOf(operations);
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
            warning = warning == null ? "" : warning;
            hasWarning = !warning.isBlank();
            hasOperations = !operations.isEmpty();
            hasAttributes = !attributes.isEmpty();
            operationCount = operations.size();
            attributeCount = attributes.size();
        }
    }

    /**
     * JMX operation metadata. This does not include invocation support.
     *
     * @param name operation name
     * @param returnType operation return type
     * @param impact JMX operation impact
     * @param parameterCount number of parameters
     * @param parameters parameter metadata
     * @param hasParameters whether parameters are available
     */
    @ReflectiveAccess
    public record Operation(String name,
                            String returnType,
                            String impact,
                            int parameterCount,
                            List<Parameter> parameters,
                            boolean hasParameters) {

        public Operation {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            hasParameters = !parameters.isEmpty();
            parameterCount = parameters.size();
        }
    }

    /**
     * JMX operation parameter metadata.
     *
     * @param name parameter name
     * @param type parameter type
     */
    @ReflectiveAccess
    public record Parameter(String name, String type) {
    }

    /**
     * JMX attribute metadata. Attribute values are intentionally not read.
     *
     * @param name attribute name
     * @param type attribute type
     * @param readable whether the attribute is readable
     * @param writable whether the attribute is writable
     */
    @ReflectiveAccess
    public record Attribute(String name, String type, boolean readable, boolean writable) {
    }

    /**
     * Panel-level warning.
     *
     * @param level warning level
     * @param message warning message
     */
    @ReflectiveAccess
    public record Warning(String level, String message) {
    }
}
