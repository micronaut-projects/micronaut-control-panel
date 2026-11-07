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
package io.micronaut.controlpanel.panels.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchControlPanelTest {

    @Test
    void showsClusterHealthAndIndices() throws IOException {
        // Elasticsearch is started by Micronaut Test Resources, see application-test.yml
        try (ApplicationContext context = ApplicationContext.run()) {
            ElasticsearchControlPanel panel = context.getBean(ElasticsearchControlPanel.class);
            assertEquals("Elasticsearch", panel.getTitle());
            assertEquals("datasource", panel.getCategory().id());

            ElasticsearchInfo body = panel.getBody();
            assertEquals(1, body.hosts().size());
            assertTrue(body.hosts().getFirst().startsWith("http://"), body.hosts().toString());
            assertNull(body.cluster(), "the dashboard card does not contact Elasticsearch");
            assertFalse(body.clientOpen());

            ElasticsearchClient client = context.getBean(ElasticsearchClient.class);
            client.indices().create(request -> request.index("orders"));
            client.index(request -> request.index("orders").id("1").document(Map.of("total", 42)).refresh(Refresh.True));

            ElasticsearchInfo detailed = panel.getDetailedBody();
            assertNull(detailed.error());
            assertTrue(detailed.clientOpen());
            assertNotNull(detailed.cluster());
            assertTrue(detailed.cluster().version().startsWith("9."), detailed.cluster().version());
            assertEquals(1, detailed.cluster().nodes());
            ElasticsearchInfo.Index orders = detailed.indices().stream()
                .filter(index -> index.name().equals("orders"))
                .findFirst()
                .orElseThrow();
            assertEquals("1", orders.documents());
            assertEquals("open", orders.status());
            assertTrue(detailed.indices().stream().noneMatch(index -> index.name().startsWith(".")));

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(ElasticsearchControlPanel.NAME).isPresent());
        }
    }

    @Test
    void unreachableClusterIsReportedByErrorType() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("elasticsearch.http-hosts", "http://user:s3cret@127.0.0.1:1"))) {
            ElasticsearchInfo detailed = context.getBean(ElasticsearchControlPanel.class).getDetailedBody();
            assertNull(detailed.cluster());
            assertNotNull(detailed.error());
            assertFalse(detailed.error().contains("127.0.0.1"), detailed.error());
            assertEquals("http://127.0.0.1:1", detailed.hosts().getFirst());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(ElasticsearchControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(ElasticsearchControlPanel.class));
        }
    }
}
