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

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Collects read-only JMX metadata for the Control Panel.
 */
@Singleton
public class JmxDiagnosticsService {

    static final String MICRONAUT_ENDPOINT_DOMAIN = "io.micronaut.management.endpoint";

    private static final Pattern SENSITIVE = Pattern.compile("(?i)(password|credential|certificate|secret|token|key|tenant|user|account)");
    private static final String JMX_CONFIGURATION_CLASS = "io.micronaut.configuration.jmx.JmxConfiguration";
    private static final String WARNING_LEVEL = "warning";

    private final BeanContext beanContext;
    private final Environment environment;

    public JmxDiagnosticsService(BeanContext beanContext, Environment environment) {
        this.beanContext = beanContext;
        this.environment = environment;
    }

    /**
     * Inspects JMX server metadata without invoking MBean operations or reading attribute values.
     *
     * @return diagnostics for rendering
     */
    public JmxDiagnostics inspect() {
        JmxDiagnostics.JmxSettings settings = settings();
        List<JmxDiagnostics.Warning> warnings = new ArrayList<>();
        if (settings.registerEndpointsDisabled()) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, "jmx.register-endpoints is false, so Micronaut endpoint MBeans are not expected to be registered."));
        }

        MBeanServer server = beanContext.findBean(MBeanServer.class).orElse(null);
        if (server == null) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, "No MBeanServer bean is available. Add micronaut-jmx or provide an MBeanServer bean to inspect JMX endpoint registrations."));
            return JmxDiagnostics.unavailable(settings, warnings);
        }

        String serverClassName = server.getClass().getName();
        String defaultDomain = safeValue(server.getDefaultDomain());
        int totalMBeanCount = server.getMBeanCount();
        Set<ObjectName> allNames = queryNames(server, null, warnings, "Could not query all MBean domains.");
        List<JmxDiagnostics.DomainSummary> domainSummaries = domainSummaries(allNames);

        Set<ObjectName> endpointNames = queryNames(server, endpointPattern(), warnings, "Could not query Micronaut endpoint MBeans.");
        List<JmxDiagnostics.EndpointMBean> endpointMBeans = endpointMBeans(server, endpointNames);
        long metadataFailures = endpointMBeans.stream().filter(JmxDiagnostics.EndpointMBean::hasWarning).count();

        if (endpointMBeans.isEmpty() && !settings.registerEndpointsDisabled()) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, "No Micronaut endpoint MBeans are registered under " + MICRONAUT_ENDPOINT_DOMAIN + ". Verify micronaut-jmx, micronaut-management, and endpoint exposure settings."));
        }
        if (metadataFailures > 0) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, metadataFailures + " Micronaut endpoint MBean metadata read failed. Rows with warnings remain visible."));
        }

        return new JmxDiagnostics(
            true,
            redactSensitive(serverClassName),
            redactSensitive(defaultDomain),
            MICRONAUT_ENDPOINT_DOMAIN,
            totalMBeanCount,
            domainSummaries.size(),
            endpointMBeans.size(),
            Math.toIntExact(metadataFailures),
            settings,
            domainSummaries,
            endpointMBeans,
            warnings,
            false,
            false,
            false
        );
    }

    private JmxDiagnostics.JmxSettings settings() {
        boolean classPresent = isPresent(JMX_CONFIGURATION_CLASS);
        boolean hasExplicitProperties = environment.containsProperty("jmx.agent-id")
            || environment.containsProperty("jmx.domain")
            || environment.containsProperty("jmx.add-to-factory")
            || environment.containsProperty("jmx.ignore-agent-not-found")
            || environment.containsProperty("jmx.register-endpoints");

        JmxDiagnostics.Setting agentId = configuredString("jmx.agent-id", null);
        JmxDiagnostics.Setting domain = configuredString("jmx.domain", null);
        JmxDiagnostics.Setting addToFactory = configuredString("jmx.add-to-factory", "true");
        JmxDiagnostics.Setting ignoreAgentNotFound = configuredString("jmx.ignore-agent-not-found", "false");
        JmxDiagnostics.Setting registerEndpoints = configuredString("jmx.register-endpoints", "true");

        return new JmxDiagnostics.JmxSettings(classPresent, hasExplicitProperties, agentId, domain, addToFactory, ignoreAgentNotFound, registerEndpoints, false);
    }

    private JmxDiagnostics.Setting configuredString(String property, @Nullable String defaultValue) {
        return environment.getProperty(property, String.class)
            .map(value -> JmxDiagnostics.Setting.configured(property, redactSensitive(value)))
            .orElseGet(() -> defaultValue == null ? JmxDiagnostics.Setting.notConfigured(property) : JmxDiagnostics.Setting.defaulted(property, defaultValue));
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, JmxDiagnosticsService.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static ObjectName endpointPattern() {
        try {
            return new ObjectName(MICRONAUT_ENDPOINT_DOMAIN + ":*");
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Micronaut endpoint MBean pattern", e);
        }
    }

    private static Set<ObjectName> queryNames(MBeanServer server, @Nullable ObjectName pattern, List<JmxDiagnostics.Warning> warnings, String message) {
        try {
            return server.queryNames(pattern, null);
        } catch (SecurityException e) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, message + " Metadata is hidden by the active security policy."));
            return Set.of();
        } catch (RuntimeException e) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, message + " " + sanitizedException(e) + "."));
            return Set.of();
        } catch (Exception e) {
            warnings.add(new JmxDiagnostics.Warning(WARNING_LEVEL, message + " " + sanitizedException(e) + "."));
            return Set.of();
        }
    }

    private static List<JmxDiagnostics.DomainSummary> domainSummaries(Set<ObjectName> allNames) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ObjectName objectName : allNames) {
            counts.merge(redactSensitive(objectName.getDomain()), 1, Integer::sum);
        }
        return counts.entrySet()
            .stream()
            .map(entry -> new JmxDiagnostics.DomainSummary(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static List<JmxDiagnostics.EndpointMBean> endpointMBeans(MBeanServer server, Set<ObjectName> endpointNames) {
        return endpointNames.stream()
            .sorted(Comparator.comparing(ObjectName::getCanonicalName))
            .map(objectName -> endpointMBean(server, objectName))
            .toList();
    }

    private static JmxDiagnostics.EndpointMBean endpointMBean(MBeanServer server, ObjectName objectName) {
        boolean registered = false;
        String warning = "";
        List<JmxDiagnostics.Operation> operations = List.of();
        List<JmxDiagnostics.Attribute> attributes = List.of();
        try {
            registered = server.isRegistered(objectName);
            MBeanInfo mBeanInfo = server.getMBeanInfo(objectName);
            operations = operations(mBeanInfo);
            attributes = attributes(mBeanInfo);
        } catch (SecurityException e) {
            warning = "Metadata hidden by the active security policy.";
        } catch (RuntimeException e) {
            warning = "Metadata unavailable: " + sanitizedException(e) + ".";
        } catch (Exception e) {
            warning = "Metadata unavailable: " + sanitizedException(e) + ".";
        }

        int infoImpactCount = impactCount(operations, "INFO");
        int actionImpactCount = impactCount(operations, "ACTION");
        int actionInfoImpactCount = impactCount(operations, "ACTION_INFO");
        int unknownImpactCount = impactCount(operations, "UNKNOWN");
        String status = warning.isBlank() ? "registered" : "metadata warning";

        return new JmxDiagnostics.EndpointMBean(
            displayName(objectName),
            endpointType(objectName),
            registered,
            status,
            warning,
            operations.size(),
            infoImpactCount,
            actionImpactCount,
            actionInfoImpactCount,
            unknownImpactCount,
            attributes.size(),
            operations,
            attributes,
            false,
            false,
            false
        );
    }

    private static List<JmxDiagnostics.Operation> operations(MBeanInfo mBeanInfo) {
        MBeanOperationInfo[] operationInfos = mBeanInfo.getOperations();
        List<JmxDiagnostics.Operation> operations = new ArrayList<>(operationInfos.length);
        for (MBeanOperationInfo operationInfo : operationInfos) {
            List<JmxDiagnostics.Parameter> parameters = parameters(operationInfo.getSignature());
            operations.add(new JmxDiagnostics.Operation(
                safeValue(operationInfo.getName()),
                safeValue(operationInfo.getReturnType()),
                impact(operationInfo.getImpact()),
                parameters.size(),
                parameters,
                false
            ));
        }
        operations.sort(Comparator.comparing(JmxDiagnostics.Operation::name)
            .thenComparing(JmxDiagnostics.Operation::returnType)
            .thenComparingInt(JmxDiagnostics.Operation::parameterCount));
        return operations;
    }

    private static List<JmxDiagnostics.Parameter> parameters(MBeanParameterInfo[] parameterInfos) {
        List<JmxDiagnostics.Parameter> parameters = new ArrayList<>(parameterInfos.length);
        for (MBeanParameterInfo parameterInfo : parameterInfos) {
            parameters.add(new JmxDiagnostics.Parameter(safeValue(parameterInfo.getName()), safeValue(parameterInfo.getType())));
        }
        return parameters;
    }

    private static List<JmxDiagnostics.Attribute> attributes(MBeanInfo mBeanInfo) {
        MBeanAttributeInfo[] attributeInfos = mBeanInfo.getAttributes();
        List<JmxDiagnostics.Attribute> attributes = new ArrayList<>(attributeInfos.length);
        for (MBeanAttributeInfo attributeInfo : attributeInfos) {
            attributes.add(new JmxDiagnostics.Attribute(
                safeValue(attributeInfo.getName()),
                safeValue(attributeInfo.getType()),
                attributeInfo.isReadable(),
                attributeInfo.isWritable()
            ));
        }
        attributes.sort(Comparator.comparing(JmxDiagnostics.Attribute::name).thenComparing(JmxDiagnostics.Attribute::type));
        return attributes;
    }

    private static int impactCount(List<JmxDiagnostics.Operation> operations, String impact) {
        return (int) operations.stream().filter(operation -> impact.equals(operation.impact())).count();
    }

    private static String impact(int impact) {
        return switch (impact) {
            case MBeanOperationInfo.INFO -> "INFO";
            case MBeanOperationInfo.ACTION -> "ACTION";
            case MBeanOperationInfo.ACTION_INFO -> "ACTION_INFO";
            default -> "UNKNOWN";
        };
    }

    private static String endpointType(ObjectName objectName) {
        return redactSensitive(objectName.getKeyProperty("type"));
    }

    private static String displayName(ObjectName objectName) {
        Map<String, String> properties = new TreeMap<>(objectName.getKeyPropertyList());
        List<String> parts = new ArrayList<>(properties.size());
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = SENSITIVE.matcher(key).find() || SENSITIVE.matcher(entry.getValue()).find() ? "[redacted]" : entry.getValue();
            parts.add(key + "=" + value);
        }
        return redactSensitive(objectName.getDomain()) + ":" + String.join(",", parts);
    }

    private static String safeValue(@Nullable String value) {
        return value == null || value.isBlank() ? "not available" : redactSensitive(value);
    }

    private static String redactSensitive(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "not available";
        }
        return SENSITIVE.matcher(value).find() ? "[redacted]" : value;
    }

    private static String sanitizedException(Exception e) {
        String simpleName = e.getClass().getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return "JMX exception";
        }
        return simpleName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
