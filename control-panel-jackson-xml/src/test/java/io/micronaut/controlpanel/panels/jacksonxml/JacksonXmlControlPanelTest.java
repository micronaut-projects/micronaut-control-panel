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
package io.micronaut.controlpanel.panels.jacksonxml;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonXmlControlPanelTest {

    @Test
    void itIsConfiguredCorrectlyAndReportsXmlRoutesMapperAndCustomizations() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "jackson.xml.default-use-wrapper", false,
            "jackson.xml.parser.auto-detect-xsi-type", true,
            "jackson.xml.generator.write-xml-declaration", true
        ))) {
            ControlPanelConfiguration cfg = context.getBean(ControlPanelConfiguration.class, Qualifiers.byName(JacksonXmlControlPanel.NAME));
            assertTrue(cfg.isEnabled());

            JacksonXmlControlPanel panel = context.getBean(JacksonXmlControlPanel.class);
            JacksonXmlControlPanel.Body body = panel.getBody();

            assertEquals("Jackson XML", panel.getTitle());
            assertEquals("fa-code", panel.getIcon());
            assertEquals(25, panel.getOrder());
            assertEquals("Serialization", panel.getCategory().name());
            assertEquals(JacksonXmlControlPanel.PanelState.POPULATED, body.state());
            assertTrue(body.mapper().dependencyPresent());
            assertTrue(body.mapper().namedMapperPresent());
            assertTrue(body.mapper().mapperClassName().contains("XmlMapper"));
            assertEquals(Boolean.FALSE, body.mapper().defaultUseWrapper());
            assertTrue(body.mapper().parserFeatures().stream().anyMatch(feature -> feature.featureName().equals("AUTO_DETECT_XSI_TYPE")));
            assertTrue(body.mapper().generatorFeatures().stream().anyMatch(feature -> feature.featureName().equals("WRITE_XML_DECLARATION")));
            assertTrue(body.customizations().stream().anyMatch(customization -> customization.implementationClassName().contains("TestXmlModule")));

            assertEquals(3, body.routes().size());
            assertTrue(body.routes().stream().anyMatch(route -> route.path().equals("/xml/books") && route.producesXml()));
            assertTrue(body.routes().stream().anyMatch(route -> route.path().equals("/xml/text") && route.produces().contains("text/xml")));
            assertTrue(body.routes().stream().anyMatch(route -> route.path().equals("/xml/vendor") && route.consumesXml() && route.producesXml()));
            assertFalse(body.routes().stream().anyMatch(route -> route.path().equals("/xml/json")));
            assertFalse(body.routes().stream().anyMatch(route -> route.path().equals("/xml/default")));
            assertEquals(String.valueOf(body.summary().xmlRoutes()), panel.getBadge());
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(JacksonXmlControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = context.getBean(ControlPanelConfiguration.class, Qualifiers.byName(JacksonXmlControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(context.containsBean(JacksonXmlControlPanel.class));
        }
    }

    @Test
    void reportsNoXmlRouteMetadataWhenRoutesDoNotDeclareXml() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "no-xml-routes"
        ))) {
            JacksonXmlControlPanel panel = context.getBean(JacksonXmlControlPanel.class);
            JacksonXmlControlPanel.Body body = panel.getBody();

            assertEquals(JacksonXmlControlPanel.PanelState.EMPTY, body.state());
            assertEquals(0, body.summary().xmlRoutes());
            assertFalse(body.hasRoutes());
            assertTrue(body.mapper().namedMapperPresent());
        }
    }

    @Test
    void xmlMediaTypeDetectionIncludesStructuredSuffixesOnly() {
        assertTrue(JacksonXmlControlPanel.isXmlMediaType("application/xml"));
        assertTrue(JacksonXmlControlPanel.isXmlMediaType("text/xml; charset=utf-8"));
        assertTrue(JacksonXmlControlPanel.isXmlMediaType(" APPLICATION/XML "));
        assertTrue(JacksonXmlControlPanel.isXmlMediaType("application/vnd.example+xml"));
        assertTrue(JacksonXmlControlPanel.isXmlMediaType("application/vnd.example.xml"));
        assertFalse(JacksonXmlControlPanel.isXmlMediaType("application/json"));
        assertFalse(JacksonXmlControlPanel.isXmlMediaType("application/"));
        assertFalse(JacksonXmlControlPanel.isXmlMediaType("xml"));
        assertFalse(JacksonXmlControlPanel.isXmlMediaType(null));
        assertFalse(JacksonXmlControlPanel.isXmlMediaType(""));
    }

    @Test
    void errorBodyAndCustomizationTargetHelpersExposeTemplateFriendlyValues() {
        JacksonXmlControlPanel.Body body = JacksonXmlControlPanel.Body.error("boom");
        assertEquals(JacksonXmlControlPanel.PanelState.ERROR, body.state());
        assertEquals("boom", body.stateMessage());
        assertEquals("Error", body.summary().mapperStatus());
        assertEquals("Not available", body.mapper().defaultUseWrapperDisplay());
        assertFalse(body.hasRoutes());
        assertFalse(body.hasCustomizations());

        JacksonXmlControlPanel.Customization customization = new JacksonXmlControlPanel.Customization(
            "module",
            "example",
            TestXmlModule.class.getName(),
            List.of(String.class.getName())
        );
        assertTrue(customization.hasTargetTypeNames());
    }

    @Test
    void detailTemplateUsesEscapedOutputAndSearchableDataTable() throws IOException {
        try (var input = getClass().getResourceAsStream("/views/jackson-xml/detail.hbs")) {
            assertNotNull(input);
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(template.contains("{{{"));
            assertTrue(template.contains("data-filter-table"));
            assertTrue(template.contains("data-filter-table-search"));
            assertTrue(template.contains("cp-data-table"));
        }
    }

    @Controller("/xml")
    @io.micronaut.context.annotation.Requires(property = "spec.name", notEquals = "no-xml-routes")
    static class XmlRouteController {

        @Get("/books")
        @Produces(MediaType.APPLICATION_XML)
        Object books() {
            return Map.of("title", "Example");
        }

        @Get("/text")
        @Produces(MediaType.TEXT_XML)
        Object text() {
            return Map.of("title", "Example");
        }

        @Post("/vendor")
        @Consumes("application/vnd.example+xml")
        @Produces("application/vnd.example+xml")
        Object vendor() {
            return Map.of("title", "Example");
        }

        @Get("/json")
        @Produces(MediaType.APPLICATION_JSON)
        Object json() {
            return Map.of("title", "Example");
        }

        @Get("/default")
        Object implicit() {
            return Map.of("title", "Example");
        }
    }

    @Controller("/plain")
    @io.micronaut.context.annotation.Requires(property = "spec.name", value = "no-xml-routes")
    static class PlainRouteController {

        @Get
        @Produces(MediaType.APPLICATION_JSON)
        Object json() {
            return Map.of("title", "Example");
        }
    }

    @Singleton
    static class TestXmlModule extends SimpleModule {
        TestXmlModule() {
            super("test-control-panel-xml");
        }
    }
}
