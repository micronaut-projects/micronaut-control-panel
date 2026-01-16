/*
 * Copyright 2017-2026
 */
package io.micronaut.controlpanel.panels.openapi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiSupportTest {

    @Test
    void parsesEnabledViewersFromSpec() {
        String spec = "swagger-ui.enabled=true, redoc.enabled=true, openapi-explorer.enabled=true, scalar.enabled=true, rapidoc.enabled=true";
        List<String> viewers = OpenApiSupport.parseEnabledViewers(spec);
        assertEquals(List.of("swagger-ui", "redoc", "openapi-explorer", "scalar", "rapidoc"), viewers);
    }
}
