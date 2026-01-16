package io.micronaut.controlpanel.panels.openapi;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiControlPanelLoaderTest {

    @Test
    void createsOnePanelPerEnabledViewerAndInfersMapping() {
        Map<String, Object> props = Map.of(
            "micronaut.openapi.views.spec", "swagger-ui.enabled=true,redoc.enabled=true,rapidoc.enabled=true",
            "micronaut.router.static-resources.rapidoc.mapping", "/rapidoc/**"
        );
        try (ApplicationContext ctx = ApplicationContext.builder().properties(props).start()) {
            ControlPanelRepository repo = ctx.getBean(ControlPanelRepository.class);
            List<ControlPanel> openapiPanels = repo.findAll()
                .stream()
                .filter(p -> p.getName().startsWith("openapi-"))
                .toList();
            // Expect 3 panels
            assertEquals(3, openapiPanels.size(), "should create one panel per enabled viewer");
            // rapidoc href should be normalized to "/rapidoc/"
            ControlPanel rapidoc = openapiPanels.stream().filter(p -> p.getName().equals("openapi-rapidoc")).findFirst().orElseThrow();
            String href = ((OpenApiViewerControlPanel.Body) rapidoc.getBody()).href();
            assertEquals("/rapidoc/", href);
            // Category should be OpenAPI
            assertEquals("openapi", rapidoc.getCategory().id());
            // No details
            assertTrue(!rapidoc.hasDetails());
        }
    }
}
