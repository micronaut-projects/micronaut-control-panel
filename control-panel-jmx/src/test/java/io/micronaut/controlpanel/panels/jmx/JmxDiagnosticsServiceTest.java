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

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmxDiagnosticsServiceTest {

    @Test
    void inspectsMicronautEndpointMBeansWithoutInvokingOperationsOrReadingAttributes() throws Exception {
        MBeanServer server = MBeanServerFactory.createMBeanServer("test-domain");
        registerEndpoint(server, "HealthEndpoint", new EndpointMBean("status", MBeanOperationInfo.INFO));
        registerEndpoint(server, "RefreshEndpoint", new EndpointMBean("refresh", MBeanOperationInfo.ACTION, "force", "boolean"));

        try (ApplicationContext ctx = ApplicationContext.run()) {
            ctx.registerSingleton(MBeanServer.class, server);

            JmxDiagnostics diagnostics = ctx.getBean(JmxDiagnosticsService.class).inspect();

            assertTrue(diagnostics.serverAvailable());
            assertEquals(2, diagnostics.endpointMBeanCount());
            assertTrue(diagnostics.hasDomainSummaries());
            assertTrue(diagnostics.domainSummaries().stream().anyMatch(domain -> domain.domain().equals(JmxDiagnosticsService.MICRONAUT_ENDPOINT_DOMAIN) && domain.mBeanCount() == 2));
            assertEquals("HealthEndpoint", diagnostics.endpointMBeans().get(0).endpointType());
            assertEquals("INFO", diagnostics.endpointMBeans().get(0).operations().get(0).impact());
            assertEquals(1, diagnostics.endpointMBeans().get(1).actionImpactCount());
            assertEquals(1, diagnostics.endpointMBeans().get(1).operations().get(0).parameterCount());
            assertEquals("boolean", diagnostics.endpointMBeans().get(1).operations().get(0).parameters().get(0).type());
        }
    }

    @Test
    void reportsRegisterEndpointsDisabledWithoutRequiringMicronautJmxConfigurationBean() throws Exception {
        MBeanServer server = MBeanServerFactory.createMBeanServer("test-domain");

        try (ApplicationContext ctx = ApplicationContext.run(Map.of("jmx.register-endpoints", false))) {
            ctx.registerSingleton(MBeanServer.class, server);

            JmxDiagnostics diagnostics = ctx.getBean(JmxDiagnosticsService.class).inspect();

            assertTrue(diagnostics.serverAvailable());
            assertFalse(diagnostics.settings().configurationClassPresent());
            assertTrue(diagnostics.settings().hasExplicitProperties());
            assertTrue(diagnostics.settings().registerEndpointsDisabled());
            assertTrue(diagnostics.warnings().stream().anyMatch(warning -> warning.message().contains("jmx.register-endpoints is false")));
        }
    }

    @Test
    void reportsUnavailableServerInsteadOfFailingWhenNoMBeanServerBeanExists() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            JmxDiagnostics diagnostics = ctx.getBean(JmxDiagnosticsService.class).inspect();

            assertFalse(diagnostics.serverAvailable());
            assertTrue(diagnostics.warnings().stream().anyMatch(warning -> warning.message().contains("No MBeanServer bean")));
        }
    }

    @Test
    void keepsEndpointRowsVisibleWhenMetadataCannotBeRead() throws Exception {
        MBeanServer server = MBeanServerFactory.createMBeanServer("test-domain");
        ObjectName brokenName = new ObjectName(JmxDiagnosticsService.MICRONAUT_ENDPOINT_DOMAIN + ":type=BrokenEndpoint");
        server.registerMBean(new EndpointMBean("broken", MBeanOperationInfo.INFO), brokenName);

        try (ApplicationContext ctx = ApplicationContext.run()) {
            ctx.registerSingleton(MBeanServer.class, failingMetadataServer(server, brokenName));

            JmxDiagnostics diagnostics = ctx.getBean(JmxDiagnosticsService.class).inspect();

            assertEquals(1, diagnostics.endpointMBeanCount());
            assertEquals(1, diagnostics.metadataFailureCount());
            assertTrue(diagnostics.endpointMBeans().get(0).hasWarning());
            assertTrue(diagnostics.endpointMBeans().get(0).warning().contains("Metadata unavailable"));
        }
    }

    @Test
    void controlPanelIsConfiguredAndCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(JmxControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(JmxControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(JmxControlPanel.class));
        }

        try (ApplicationContext ctx = ApplicationContext.run()) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(JmxControlPanel.NAME));
            JmxControlPanel panel = ctx.getBean(JmxControlPanel.class);

            assertEquals("JMX", cfg.getTitle());
            assertEquals("fa-network-wired", panel.getIcon());
            assertEquals("diagnostics", panel.getCategory().id());
            assertNotNull(panel.getBody());
        }
    }

    private static void registerEndpoint(MBeanServer server, String type, DynamicMBean mBean) throws Exception {
        server.registerMBean(mBean, new ObjectName(JmxDiagnosticsService.MICRONAUT_ENDPOINT_DOMAIN + ":type=" + type));
    }

    private static MBeanServer failingMetadataServer(MBeanServer delegate, ObjectName brokenName) {
        return (MBeanServer) Proxy.newProxyInstance(
            JmxDiagnosticsServiceTest.class.getClassLoader(),
            new Class<?>[] { MBeanServer.class },
            (proxy, method, args) -> {
                if ("getMBeanInfo".equals(method.getName()) && args != null && args.length == 1 && brokenName.equals(args[0])) {
                    throw new IllegalStateException("secret token should not be rendered");
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    private static final class EndpointMBean implements DynamicMBean {

        private final String operationName;
        private final int impact;
        private final String parameterName;
        private final String parameterType;

        EndpointMBean(String operationName, int impact) {
            this(operationName, impact, null, null);
        }

        EndpointMBean(String operationName, int impact, String parameterName, String parameterType) {
            this.operationName = operationName;
            this.impact = impact;
            this.parameterName = parameterName;
            this.parameterType = parameterType;
        }

        @Override
        public Object getAttribute(String attribute) {
            throw new AssertionError("The JMX panel must not read attribute values");
        }

        @Override
        public void setAttribute(Attribute attribute) {
            throw new AssertionError("The JMX panel must not write attribute values");
        }

        @Override
        public AttributeList getAttributes(String[] attributes) {
            throw new AssertionError("The JMX panel must not read attribute values");
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes) {
            throw new AssertionError("The JMX panel must not write attribute values");
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature) {
            throw new AssertionError("The JMX panel must not invoke operations");
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            MBeanParameterInfo[] signature = parameterName == null ? new MBeanParameterInfo[0] : new MBeanParameterInfo[] {
                new MBeanParameterInfo(parameterName, parameterType, "test parameter")
            };
            return new MBeanInfo(
                getClass().getName(),
                "test endpoint MBean",
                new MBeanAttributeInfo[] {
                    new MBeanAttributeInfo("State", String.class.getName(), "state metadata only", true, false, false)
                },
                null,
                new MBeanOperationInfo[] {
                    new MBeanOperationInfo(operationName, "test operation", signature, String.class.getName(), impact)
                },
                null
            );
        }
    }

}
