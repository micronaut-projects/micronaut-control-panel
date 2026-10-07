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
package io.micronaut.controlpanel.panels.opensearch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenSearchControlPanelTest {

    @Test
    void panelIsCreatedWhenOpenSearchClientBeanExists() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "OpenSearchControlPanelTest"))) {
            assertTrue(context.containsBean(OpenSearchControlPanel.class));
            assertEquals("opensearch-default", context.getBean(OpenSearchControlPanel.class).getName());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "OpenSearchControlPanelTest",
            OpenSearchControlPanel.ENABLED_PROPERTY, false
        ))) {
            assertFalse(context.containsBean(OpenSearchDiagnosticsService.class));
            assertFalse(context.containsBean(OpenSearchControlPanel.class));
        }
    }

    @Test
    void panelIsCreatedForEachOpenSearchClientBean() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "OpenSearchControlPanelMultiClientTest"))) {
            Set<String> names = context.getBeansOfType(OpenSearchControlPanel.class)
                .stream()
                .map(OpenSearchControlPanel::getName)
                .collect(Collectors.toSet());

            assertEquals(Set.of("opensearch-default", "opensearch-analytics"), names);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "OpenSearchControlPanelTest")
    static final class TestFactory {
        @Singleton
        OpenSearchClient openSearchClient() {
            return mock(OpenSearchClient.class);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "OpenSearchControlPanelMultiClientTest")
    static final class MultiClientTestFactory {
        @Singleton
        @Named("default")
        OpenSearchClient defaultOpenSearchClient() {
            return mock(OpenSearchClient.class);
        }

        @Singleton
        @Named("analytics")
        OpenSearchClient analyticsOpenSearchClient() {
            return mock(OpenSearchClient.class);
        }
    }
}
