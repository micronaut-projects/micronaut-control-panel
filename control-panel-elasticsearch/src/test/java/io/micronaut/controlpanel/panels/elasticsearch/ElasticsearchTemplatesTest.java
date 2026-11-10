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

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.ui.ControlPanelRenderer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the panel templates with the Control Panel Handlebars instance, so that a renamed accessor or a template
 * error fails the build instead of the page.
 */
class ElasticsearchTemplatesTest {

    private static ApplicationContext context;
    private static ControlPanelRenderer renderer;

    @BeforeAll
    static void start() {
        // No Elasticsearch client is created: only the renderer is used
        context = ApplicationContext.run(Map.of("elasticsearch.http-hosts", "http://localhost:9200"));
        renderer = context.getBean(ControlPanelRenderer.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void rendersAConnectedCluster() {
        var info = new ElasticsearchInfo(
            List.of("http://es-1:9200"),
            true,
            new ElasticsearchInfo.Cluster("<script>cluster</script>", "9.1.5", "red", 3, 10, 2),
            List.of(new ElasticsearchInfo.Index("books", "green", "open", "1,024", "2.0 MB", "1", "1")),
            5,
            null
        );

        String body = render("body", info);
        assertTrue(body.contains("<code>http://es-1:9200</code>"), body);
        assertTrue(body.contains("The application has an open client."), body);

        String detail = render("detail", info);
        assertTrue(detail.contains("&lt;script&gt;cluster&lt;/script&gt;"), "values are escaped");
        assertFalse(detail.contains("<script>cluster"), "values are escaped");
        assertTrue(detail.contains("badge-destructive\">red</span>"), detail);
        assertTrue(detail.contains("2 unassigned"), detail);
        assertTrue(detail.contains("<code class=\"cp-table-code\">books</code>"), detail);
        assertTrue(detail.contains("Showing the first 1 of 5 indices."), detail);
        assertFalse(detail.contains("Unable to read the cluster"), detail);
    }

    @Test
    void rendersAnUnreachableCluster() {
        var info = new ElasticsearchInfo(List.of("http://es-1:9200"), true, null, List.of(), 0, "ConnectException");

        String detail = render("detail", info);
        assertTrue(detail.contains("Unable to read the cluster: ConnectException."), detail);
        assertFalse(detail.contains("Indices"), detail);
    }

    @Test
    void rendersAnApplicationWithoutClient() {
        var info = new ElasticsearchInfo(List.of(), false, null, List.of(), 0, null);

        String body = render("body", info);
        assertTrue(body.contains("No Elasticsearch hosts are configured."), body);
        assertTrue(body.contains("No client has been created yet."), body);
        assertTrue(render("detail", info).contains("none"));
    }

    private static String render(String template, ElasticsearchInfo info) {
        return renderer.render("views/elasticsearch/" + template, Map.of("controlPanel", Map.of("body", info, "detailedBody", info)));
    }
}
