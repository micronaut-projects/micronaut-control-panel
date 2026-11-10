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
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Write;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmxControlPanelTest {

    private static final String SPEC = "JmxControlPanelTest";

    @Test
    void listsEndpointMBeansWithoutInvokingThem() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            JmxControlPanel panel = context.getBean(JmxControlPanel.class);
            assertEquals("JMX", panel.getTitle());
            assertEquals("", panel.getBadge());

            JmxInfo body = panel.getBody();
            assertTrue(body.mbeanCount() > 0);
            assertTrue(body.domains().stream().anyMatch(domain -> domain.name().equals("java.lang")), body.domains().toString());
            assertTrue(body.settings().registerEndpoints());

            JmxInfo.Endpoint orders = body.endpoints().stream()
                .filter(endpoint -> endpoint.endpoint().equals("orders"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(body.endpoints().toString()));
            assertTrue(orders.registered(), orders.toString());
            assertEquals(List.of("reset", "status"), orders.operations().stream().map(JmxInfo.Operation::name).toList());
            JmxInfo.Operation reset = orders.operations().getFirst();
            assertEquals(List.of("String"), reset.signature());
            assertEquals("String", reset.parameters());
            assertTrue(body.registeredEndpoints() > 0);
            assertEquals("ACTION", reset.impact());
            assertEquals("INFO", orders.operations().get(1).impact());
            assertEquals(0, context.getBean(OrdersEndpoint.class).calls, "operations are never invoked");

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(JmxControlPanel.NAME).isPresent());
        }
    }

    @Test
    void reportsEndpointsThatAreNotRegistered() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC, "jmx.register-endpoints", false))) {
            JmxInfo body = context.getBean(JmxControlPanel.class).getBody();
            assertFalse(body.settings().registerEndpoints());
            assertTrue(body.endpoints().stream().noneMatch(JmxInfo.Endpoint::registered), body.endpoints().toString());
            assertEquals(0, body.registeredEndpoints());
        }
    }

    @Test
    void typesAreShortened() {
        assertEquals("String", JmxInspector.shortType("java.lang.String"));
        assertEquals("Map", JmxInspector.shortType("java.util.Map"));
        assertEquals("String[]", JmxInspector.shortType("[Ljava.lang.String;"));
        assertEquals("java.util.concurrent.Future", JmxInspector.shortType("java.util.concurrent.Future"));
    }

    @Test
    void attributesAndImpactsAreDescribed() {
        assertEquals("read-write", JmxInspector.attribute(new MBeanAttributeInfo("a", "java.lang.String", "", true, true, false)).access());
        assertEquals("write-only", JmxInspector.attribute(new MBeanAttributeInfo("a", "int", "", false, true, false)).access());
        JmxInfo.Attribute readOnly = JmxInspector.attribute(new MBeanAttributeInfo("a", "java.util.List", "", true, false, false));
        assertEquals("read-only", readOnly.access());
        assertEquals("List", readOnly.type());
        assertEquals("ACTION_INFO", JmxInspector.impact(MBeanOperationInfo.ACTION_INFO));
        assertEquals("UNKNOWN", JmxInspector.impact(MBeanOperationInfo.UNKNOWN));
        assertEquals("void", JmxInspector.shortType(null));
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(JmxControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(JmxControlPanel.class));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Endpoint("orders")
    static class OrdersEndpoint {

        int calls;

        @Read
        String status() {
            calls++;
            return "ok";
        }

        @Write
        void reset(String reason) {
            calls++;
        }
    }
}
