package io.micronaut.controlpanel.ui.util;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.refresh.RefreshEndpoint;
import io.micronaut.management.endpoint.stop.ServerStopEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndpointUtilsTest {

    private ApplicationContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.stop();
        }
    }

    @Test
    void itCanCheckIfEndpointIsSensitive() {
        var configuration = new java.util.HashMap<String, Object>();
        configuration.put("endpoints.all.enabled", true);

        ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .propertySources(PropertySource.of(configuration))
            .start();
        var endpoint = ctx.getBean(RefreshEndpoint.class);
        assertEquals(Endpoint.SENSITIVE, EndpointUtils.isSensitive(endpoint, ctx));
        ctx.stop();

        configuration.put("endpoints.all.sensitive", true);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        endpoint = ctx.getBean(RefreshEndpoint.class);
        assertTrue(EndpointUtils.isSensitive(endpoint, ctx));
        ctx.stop();

        configuration.remove("endpoints.all.sensitive");
        configuration.put("endpoints.refresh.sensitive", true);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        endpoint = ctx.getBean(RefreshEndpoint.class);
        assertTrue(EndpointUtils.isSensitive(endpoint, ctx));
        ctx.stop();

        configuration.remove("endpoints.refresh.sensitive");
        configuration.put("endpoints.all.sensitive", false);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        endpoint = ctx.getBean(RefreshEndpoint.class);
        assertFalse(EndpointUtils.isSensitive(endpoint, ctx));
        ctx.stop();

        configuration.remove("endpoints.all.sensitive");
        configuration.put("endpoints.refresh.sensitive", false);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        endpoint = ctx.getBean(RefreshEndpoint.class);
        assertFalse(EndpointUtils.isSensitive(endpoint, ctx));
        ctx.stop();

        configuration.clear();
        configuration.put("endpoints.all.enabled", true);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        var stopEndpoint = ctx.getBean(ServerStopEndpoint.class);
        assertEquals(Endpoint.SENSITIVE, EndpointUtils.isSensitive(stopEndpoint, ctx));
        ctx.stop();

        configuration.put("endpoints.all.sensitive", true);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        stopEndpoint = ctx.getBean(ServerStopEndpoint.class);
        assertTrue(EndpointUtils.isSensitive(stopEndpoint, ctx));
        ctx.stop();

        configuration.remove("endpoints.all.sensitive");
        configuration.put("endpoints.stop.sensitive", true);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        stopEndpoint = ctx.getBean(ServerStopEndpoint.class);
        assertTrue(EndpointUtils.isSensitive(stopEndpoint, ctx));
        ctx.stop();

        configuration.remove("endpoints.stop.sensitive");
        configuration.put("endpoints.all.sensitive", false);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        stopEndpoint = ctx.getBean(ServerStopEndpoint.class);
        assertFalse(EndpointUtils.isSensitive(stopEndpoint, ctx));
        ctx.stop();

        configuration.remove("endpoints.all.sensitive");
        configuration.put("endpoints.stop.sensitive", false);
        ctx = ApplicationContext.builder().deduceEnvironment(false).propertySources(PropertySource.of(configuration)).start();
        stopEndpoint = ctx.getBean(ServerStopEndpoint.class);
        assertFalse(EndpointUtils.isSensitive(stopEndpoint, ctx));
    }
}
