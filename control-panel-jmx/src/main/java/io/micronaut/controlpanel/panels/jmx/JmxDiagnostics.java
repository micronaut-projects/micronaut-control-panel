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

import java.util.List;

/**
 * Read-only JMX diagnostics rendered by the JMX Control Panel.
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
                             JmxSettings settings,
                             List<DomainSummary> domainSummaries,
                             List<EndpointMBean> endpointMBeans,
                             List<Warning> warnings,
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
     */
    @ReflectiveAccess
    public record JmxSettings(boolean configurationClassPresent,
                              boolean hasExplicitProperties,
                              Setting agentId,
                              Setting domain,
                              Setting addToFactory,
                              Setting ignoreAgentNotFound,
                              Setting registerEndpoints,
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
     */
    @ReflectiveAccess
    public record DomainSummary(String domain, int mBeanCount) {
    }

    /**
     * One Micronaut endpoint MBean and the metadata that could be read safely.
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
     */
    @ReflectiveAccess
    public record Parameter(String name, String type) {
    }

    /**
     * JMX attribute metadata. Attribute values are intentionally not read.
     */
    @ReflectiveAccess
    public record Attribute(String name, String type, boolean readable, boolean writable) {
    }

    /**
     * Panel-level warning.
     */
    @ReflectiveAccess
    public record Warning(String level, String message) {
    }
}
