/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.openapi;

import io.micronaut.context.annotation.Property;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "micronaut.router.static-resources.swagger-ui.mapping", value = "/swagger-ui/**")
@Property(name = "micronaut.router.static-resources.redoc.mapping", value = "/redoc/**")
@Property(name = "micronaut.router.static-resources.rapidoc.mapping", value = "/rapidoc/**")
class OpenAPIControlPanelLoaderTest {

    @Test
    void testOpenAPIViewersAreLoaded(ControlPanelRepository repository) {
        // Check that OpenAPI control panels are loaded
        var swaggerPanel = repository.findByName("openapi-swagger-ui");
        assertTrue(swaggerPanel.isPresent(), "Swagger UI panel should be present");
        
        var redocPanel = repository.findByName("openapi-redoc");
        assertTrue(redocPanel.isPresent(), "Redoc panel should be present");
        
        var rapidocPanel = repository.findByName("openapi-rapidoc");
        assertTrue(rapidocPanel.isPresent(), "RapiDoc panel should be present");
    }

    @Test
    void testOpenAPIViewerProperties(ControlPanelRepository repository) {
        var panel = (OpenAPIViewerControlPanel) repository.findByName("openapi-swagger-ui").get();
        
        assertNotNull(panel);
        assertEquals("Swagger UI", panel.getTitle());
        assertEquals("fa-file-code", panel.getIcon());
        assertFalse(panel.hasDetails(), "OpenAPI panels should not have details");
        
        var body = panel.getBody();
        assertNotNull(body);
        assertEquals("swagger-ui", body.viewerName());
        assertEquals("/swagger-ui/", body.viewerUrl());
    }

    @Test
    void testOpenAPICategory(ControlPanelRepository repository) {
        var panel = (OpenAPIViewerControlPanel) repository.findByName("openapi-swagger-ui").get();
        
        var category = panel.getCategory();
        assertNotNull(category);
        assertEquals("openapi", category.id());
        assertEquals("OpenAPI", category.name());
        assertEquals("fa-file-code", category.iconClass());
    }

    @Test
    void testViewerUrlInference(ControlPanelRepository repository) {
        var swaggerPanel = (OpenAPIViewerControlPanel) repository.findByName("openapi-swagger-ui").get();
        assertEquals("/swagger-ui/", swaggerPanel.getBody().viewerUrl());
        
        var redocPanel = (OpenAPIViewerControlPanel) repository.findByName("openapi-redoc").get();
        assertEquals("/redoc/", redocPanel.getBody().viewerUrl());
        
        var rapidocPanel = (OpenAPIViewerControlPanel) repository.findByName("openapi-rapidoc").get();
        assertEquals("/rapidoc/", rapidocPanel.getBody().viewerUrl());
    }
}
