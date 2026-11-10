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

import io.micronaut.configuration.jmx.JmxConfiguration;
import io.micronaut.configuration.jmx.context.NameGenerator;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.endpoint.annotation.Endpoint;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Collects the MBean server domains and the management endpoint MBeans that Micronaut JMX registers.
 *
 * <p>Only metadata is read: operations are never invoked, endpoint attribute values are never read, and MBeans are never
 * registered or unregistered.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = JmxConfiguration.class)
public class JmxInspector {

    private static final Logger LOG = LoggerFactory.getLogger(JmxInspector.class);
    private static final String ENDPOINT_NAME_GENERATOR = "endpoint";
    private static final String JAVA_LANG = "java.lang.";
    private static final String JAVA_UTIL = "java.util.";

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public JmxInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return the MBean server and the endpoint MBeans
     */
    public JmxInfo inspect() {
        MBeanServer server = mBeanServer();
        Map<String, Integer> domains = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int count = 0;
        for (ObjectName name : server.queryNames(null, null)) {
            domains.merge(name.getDomain(), 1, Integer::sum);
            count++;
        }
        return new JmxInfo(
            implementation(server),
            String.valueOf(server.getDefaultDomain()),
            count,
            domains.entrySet().stream().map(entry -> new JmxInfo.Domain(entry.getKey(), entry.getValue())).toList(),
            settings(),
            endpoints(server)
        );
    }

    private MBeanServer mBeanServer() {
        try {
            return beanContext.findBean(MBeanServer.class).orElseGet(ManagementFactory::getPlatformMBeanServer);
        } catch (RuntimeException e) {
            LOG.debug("Unable to get the MBean server bean, using the platform MBean server", e);
            return ManagementFactory.getPlatformMBeanServer();
        }
    }

    private static String implementation(MBeanServer server) {
        try {
            Object name = server.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "ImplementationName");
            Object version = server.getAttribute(MBeanServerDelegate.DELEGATE_NAME, "ImplementationVersion");
            return name + " " + version;
        } catch (JMException | RuntimeException e) {
            LOG.debug("Unable to read the MBean server implementation", e);
            return server.getClass().getName();
        }
    }

    private JmxInfo.Settings settings() {
        Optional<JmxConfiguration> configuration = beanContext.findBean(JmxConfiguration.class);
        return configuration
            .map(c -> new JmxInfo.Settings(c.getDomain(), c.getAgentId(), c.isRegisterEndpoints(), c.isAddToFactory()))
            .orElseGet(() -> new JmxInfo.Settings(null, null, false, false));
    }

    private List<JmxInfo.Endpoint> endpoints(MBeanServer server) {
        Optional<NameGenerator> nameGenerator;
        try {
            nameGenerator = beanContext.findBean(NameGenerator.class, Qualifiers.byName(ENDPOINT_NAME_GENERATOR));
        } catch (RuntimeException e) {
            LOG.debug("Unable to get the JMX endpoint name generator", e);
            return List.of();
        }
        if (nameGenerator.isEmpty()) {
            return List.of();
        }
        List<JmxInfo.Endpoint> endpoints = new ArrayList<>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(Endpoint.class))) {
            try {
                ObjectName name = nameGenerator.get().generate(definition);
                endpoints.add(endpoint(server, endpointId(definition), name));
            } catch (JMException | RuntimeException e) {
                LOG.debug("Unable to read the JMX MBean of endpoint {}", definition.getBeanType().getName(), e);
            }
        }
        endpoints.sort(Comparator.comparing(JmxInfo.Endpoint::objectName));
        return endpoints;
    }

    private static String endpointId(BeanDefinition<?> definition) {
        return definition.stringValue(Endpoint.class, "id")
            .or(() -> definition.stringValue(Endpoint.class))
            .filter(id -> !id.isBlank())
            .orElse(definition.getBeanType().getSimpleName());
    }

    private static JmxInfo.Endpoint endpoint(MBeanServer server, String id, ObjectName name) throws JMException {
        if (!server.isRegistered(name)) {
            return new JmxInfo.Endpoint(id, name.getCanonicalName(), false, List.of(), List.of());
        }
        MBeanInfo info = server.getMBeanInfo(name);
        List<JmxInfo.Operation> operations = Arrays.stream(info.getOperations())
            .map(JmxInspector::operation)
            .sorted(Comparator.comparing(JmxInfo.Operation::name))
            .toList();
        List<JmxInfo.Attribute> attributes = Arrays.stream(info.getAttributes())
            .map(JmxInspector::attribute)
            .sorted(Comparator.comparing(JmxInfo.Attribute::name))
            .toList();
        return new JmxInfo.Endpoint(id, name.getCanonicalName(), true, operations, attributes);
    }

    private static JmxInfo.Operation operation(MBeanOperationInfo operation) {
        return new JmxInfo.Operation(
            operation.getName(),
            Arrays.stream(operation.getSignature()).map(MBeanParameterInfo::getType).map(JmxInspector::shortType).toList(),
            shortType(operation.getReturnType()),
            impact(operation.getImpact())
        );
    }

    static JmxInfo.Attribute attribute(MBeanAttributeInfo attribute) {
        String access;
        if (attribute.isReadable() && attribute.isWritable()) {
            access = "read-write";
        } else if (attribute.isWritable()) {
            access = "write-only";
        } else {
            access = "read-only";
        }
        return new JmxInfo.Attribute(attribute.getName(), shortType(attribute.getType()), access);
    }

    static String impact(int impact) {
        return switch (impact) {
            case MBeanOperationInfo.INFO -> "INFO";
            case MBeanOperationInfo.ACTION -> "ACTION";
            case MBeanOperationInfo.ACTION_INFO -> "ACTION_INFO";
            default -> "UNKNOWN";
        };
    }

    /**
     * @param type a fully qualified type name
     * @return the type name without the {@code java.lang} or {@code java.util} package, with object arrays shown as
     * {@code Type[]}
     */
    static String shortType(@Nullable String type) {
        if (type == null) {
            return "void";
        }
        if (type.startsWith("[L") && type.endsWith(";")) {
            return shortType(type.substring(2, type.length() - 1)) + "[]";
        }
        for (String prefix : List.of(JAVA_LANG, JAVA_UTIL)) {
            if (type.startsWith(prefix) && type.indexOf('.', prefix.length()) < 0) {
                return type.substring(prefix.length());
            }
        }
        return type;
    }
}
