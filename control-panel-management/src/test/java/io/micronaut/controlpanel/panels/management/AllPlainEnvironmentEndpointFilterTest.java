package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.management.endpoint.env.EnvironmentEndpoint;
import io.micronaut.management.endpoint.env.EnvironmentEndpointFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllPlainEnvironmentEndpointFilterTest {

    @Test
    void filterIsNotEnabledByDefault() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            var filters = ctx.getBeansOfType(EnvironmentEndpointFilter.class);
            assertTrue(filters.isEmpty());
        }
    }

    @Test
    void filterIsEnabledWhenPropertyIsTrue() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY, true))) {
            var filter = ctx.getBean(AllPlainEnvironmentEndpointFilter.class);
            assertNotNull(filter);
        }
    }

    @Test
    void filterIsNotEnabledWhenPropertyIsFalse() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY, false))) {
            var filters = ctx.getBeansOfType(EnvironmentEndpointFilter.class);
            assertTrue(filters.isEmpty());
        }
    }

    @Test
    void environmentEndpointFilterAppliedWhenShowValuesEnabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(
            "endpoints.env.enabled", true,
            AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY, true,
            "test.password", "secret123",
            "test.username", "john"
        ))) {
            var endpoint = ctx.getBean(EnvironmentEndpoint.class);
            var filters = ctx.getBeansOfType(EnvironmentEndpointFilter.class);
            var environmentInfo = endpoint.getEnvironmentInfo();

            java.util.Map<String,Object> allProps = new java.util.HashMap<>();
            var propertySources = (java.util.Collection<?>) environmentInfo.get("propertySources");
            for (Object ps : propertySources) {
                Object props = (ps instanceof java.util.Map) ? ((java.util.Map<?,?>) ps).get("properties") : null;
                if (props instanceof java.util.Map<?,?> map) {
                    for (var e : map.entrySet()) {
                        allProps.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }
            java.util.function.Function<Object,Object> extractValue = (obj) -> {
                if (obj == null) return null;
                if (obj instanceof java.util.Map<?,?> m) return m.get("value");
                try { return obj.getClass().getField("value").get(obj); } catch (Exception ignored) {}
                return String.valueOf(obj);
            };

            var username = extractValue.apply(allProps.get("test.username"));
            var password = extractValue.apply(allProps.get("test.password"));

            assertEquals(1, filters.size());
            assertTrue(filters.iterator().next() instanceof AllPlainEnvironmentEndpointFilter);
            assertNotNull(environmentInfo);
            assertEquals("john", username);
            assertNotEquals("secret123", password);
        }
    }

    @Test
    void noEnvironmentEndpointFilterWhenShowValuesDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(
            "endpoints.env.enabled", true,
            AllPlainEnvironmentEndpointFilter.ENABLED_PROPERTY, false
        ))) {
            var filters = ctx.getBeansOfType(EnvironmentEndpointFilter.class);
            assertTrue(filters.isEmpty());
        }
    }
}
