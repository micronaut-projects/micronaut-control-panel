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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.endpoint.info.InfoAggregator;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InfoControlPanelTest {

    @Test
    void itIsConfiguredCorrectlyAndRegistered() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "endpoints.info.enabled", true,
            "info.application.name", "Control Panel",
            "info.application.version", "2.0.0"
        ))) {
            InfoControlPanel panel = ctx.getBean(InfoControlPanel.class);
            assertEquals("Application Info", panel.getTitle());
            assertEquals("fa-info-circle", panel.getIcon());
            assertEquals(30, panel.getOrder());

            assertTrue(ctx.getBean(ControlPanelRepository.class).findByName(InfoControlPanel.NAME).isPresent());
            assertEquals("", panel.getBadge());

            InfoControlPanel.InfoNode application = find(panel.getBody().sections(), "application");
            assertEquals("Application", application.displayName());
            assertEquals("Control Panel", find(application.children(), "name").value());
            assertEquals("2.0.0", find(application.children(), "version").value());

            assertSummaryItem(panel.getBody().summaryItems(), "Name", "Control Panel");
            assertSummaryItem(panel.getBody().summaryItems(), "Version", "2.0.0");
        }
    }

    @Test
    void itAggregatesCustomNestedInfoSources() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "endpoints.info.enabled", true,
            "spec.name", "custom-info"
        ))) {
            InfoControlPanel panel = ctx.getBean(InfoControlPanel.class);

            InfoControlPanel.InfoNode custom = find(panel.getBody().sections(), "custom");
            InfoControlPanel.InfoNode nested = find(custom.children(), "nested");
            assertEquals("true", find(nested.children(), "enabled").value());
            assertEquals("one", find(find(custom.children(), "items").children(), "0").value());
            assertEquals("two", find(find(custom.children(), "items").children(), "1").value());
            assertEquals("<script>alert(1)</script>", find(custom.children(), "html").value());
            assertTrue(panel.getBody().rows().stream().anyMatch(row ->
                "Custom".equals(row.section()) && "Nested / Enabled".equals(row.key()) && "true".equals(row.value())));
        }
    }

    @Test
    void itCanBeDisabled() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(InfoControlPanel.ENABLED_PROPERTY, false))) {
            ControlPanelConfiguration cfg = ctx.getBean(ControlPanelConfiguration.class, Qualifiers.byName(InfoControlPanel.NAME));
            assertFalse(cfg.isEnabled());
            assertFalse(ctx.containsBean(InfoControlPanel.class));
        }
    }

    @Test
    void itIsNotRegisteredWhenInfoEndpointBeanIsAbsent() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("endpoints.info.enabled", false))) {
            assertFalse(ctx.containsBean(InfoControlPanel.class));
            assertTrue(ctx.getBean(ControlPanelRepository.class).findByName(InfoControlPanel.NAME).isEmpty());
        }
    }

    @Test
    void itHandlesEmptyInfoOutput() {
        InfoControlPanel panel = panelFor(Map.of());

        assertTrue(panel.getBody().sections().isEmpty());
        assertTrue(panel.getBody().summaryItems().isEmpty());
        assertEquals("", panel.getBadge());
    }

    @Test
    void itBuildsReadableNestedNodes() {
        Map<String, Object> build = new LinkedHashMap<>();
        build.put("commitId", "abc123");
        build.put("tags", List.of("local", "test"));

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("build-info", build);

        InfoControlPanel.InfoNode buildInfo = find(panelFor(info).getBody().sections(), "build-info");

        assertEquals("Build Info", buildInfo.displayName());
        assertEquals("abc123", find(buildInfo.children(), "commitId").value());
        assertEquals("[0]", find(find(buildInfo.children(), "tags").children(), "0").displayName());
    }

    @Test
    void infoTemplatesDoNotUseRawHandlebarsOutput() throws IOException {
        assertTemplateDoesNotUseRawOutput("views/info/body.hbs");
        assertTemplateDoesNotUseRawOutput("views/info/detail.hbs");
    }

    private static InfoControlPanel panelFor(Map<String, Object> info) {
        ControlPanelConfiguration configuration = new ControlPanelConfiguration(InfoControlPanel.NAME);
        configuration.setTitle("Application Info");
        InfoAggregator<Map<String, Object>> aggregator = sources -> Mono.just(info);
        return new InfoControlPanel(aggregator, new InfoSource[0], configuration);
    }

    private static InfoControlPanel.InfoNode find(List<InfoControlPanel.InfoNode> nodes, String name) {
        return nodes.stream()
            .filter(node -> name.equals(node.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Node not found: " + name));
    }

    private static void assertSummaryItem(List<InfoControlPanel.SummaryItem> items, String label, String value) {
        assertTrue(items.stream().anyMatch(item -> label.equals(item.label()) && value.equals(item.value())),
            () -> "Summary item not found: " + label + "=" + value);
    }

    private static void assertTemplateDoesNotUseRawOutput(String path) throws IOException {
        try (var inputStream = InfoControlPanelTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(inputStream, () -> "Missing template: " + path);
            String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(template.contains("{{{"), () -> path + " must not use triple-stash raw output");
            assertFalse(template.contains("{{&"), () -> path + " must not use raw output");
        }
    }

    @Factory
    static class CustomInfoSourceFactory {

        @Singleton
        @Requires(property = "spec.name", value = "custom-info")
        InfoSource customInfoSource() {
            return () -> {
                Map<String, Object> nested = new LinkedHashMap<>();
                nested.put("enabled", true);

                Map<String, Object> custom = new LinkedHashMap<>();
                custom.put("nested", nested);
                custom.put("items", List.of("one", "two"));
                custom.put("html", "<script>alert(1)</script>");

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("custom", custom);
                return Mono.just(PropertySource.of("custom-info", info));
            };
        }
    }
}
