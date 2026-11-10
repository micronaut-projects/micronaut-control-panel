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
package io.micronaut.controlpanel.panels.neo4j;

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
class Neo4jTemplatesTest {

    private static ApplicationContext context;
    private static ControlPanelRenderer renderer;

    @BeforeAll
    static void start() {
        // No driver is created: only the renderer is used
        context = ApplicationContext.run(Map.of("neo4j.uri", "bolt://localhost:7687"));
        renderer = context.getBean(ControlPanelRenderer.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void rendersTheServerDatabasesAndGraph() {
        var info = new Neo4jInfo(
            "bolt://localhost:7687",
            "basic (username and password configured)",
            true,
            new Neo4jInfo.Server("Neo4j Kernel", "5.26.0", "community"),
            List.of(new Neo4jInfo.Database("neo4j", "online", true), new Neo4jInfo.Database("archive", "offline", false)),
            new Neo4jInfo.Graph(42, 7, List.of("<script>Person</script>"), List.of("KNOWS")),
            null
        );

        String body = render("body", info);
        assertTrue(body.contains("<code>bolt://localhost:7687</code>"), body);
        assertTrue(body.contains("The application has an open driver."), body);

        String detail = render("detail", info);
        assertTrue(detail.contains("Neo4j Kernel 5.26.0 <span class=\"badge badge-secondary\">community</span>"), detail);
        assertTrue(detail.contains("<code class=\"cp-table-code\">neo4j</code> <span class=\"badge badge-secondary\">default</span>"), detail);
        assertTrue(detail.contains("<code class=\"cp-table-code\">archive</code></td>"), detail);
        assertTrue(detail.contains("<span class=\"badge badge-destructive\">offline</span>"), detail);
        assertTrue(detail.contains("<dd>42</dd>"), detail);
        assertTrue(detail.contains("&lt;script&gt;Person&lt;/script&gt;"), "labels are escaped");
        assertFalse(detail.contains("<script>Person"), "labels are escaped");
        assertTrue(detail.contains("KNOWS"), detail);
        assertFalse(detail.contains("Unable to read the server"), detail);
    }

    @Test
    void rendersOnlyTheErrorTypeWhenTheServerIsUnreachable() {
        var info = new Neo4jInfo("bolt://localhost:7687", "none", false, null, List.of(), null, "ServiceUnavailableException");

        assertTrue(render("body", info).contains("No driver has been created yet."));
        String detail = render("detail", info);
        assertTrue(detail.contains("Unable to read the server: ServiceUnavailableException."), detail);
        assertFalse(detail.contains("Databases</h3>"), detail);
        assertFalse(detail.contains("Default database</h3>"), detail);
    }

    private static String render(String template, Neo4jInfo info) {
        return renderer.render("views/neo4j/" + template, Map.of("controlPanel", Map.of("body", info, "detailedBody", info)));
    }
}
