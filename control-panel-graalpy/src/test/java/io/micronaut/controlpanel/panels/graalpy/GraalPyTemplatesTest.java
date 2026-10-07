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
package io.micronaut.controlpanel.panels.graalpy;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalPyTemplatesTest {

    private final Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader("/", ".hbs"));

    GraalPyTemplatesTest() {
        StringHelpers.register(handlebars);
    }

    @Test
    void detailRendersEverySectionForAPopulatedPanel() throws IOException {
        String html = renderDetail(populatedBody());

        assertTrue(html.contains("aria-controls=\"panel-graalpy-beans\""));
        assertTrue(html.contains("aria-controls=\"panel-graalpy-pool\""));
        assertTrue(html.contains("aria-controls=\"panel-graalpy-configuration\""));
        assertTrue(html.contains("aria-controls=\"panel-graalpy-vfs\""));
        assertTrue(html.contains("aria-controls=\"panel-graalpy-runtime\""));

        assertTrue(html.contains("dealerModule"));
        assertTrue(html.contains("String deal(String name, int count)"));
        assertTrue(html.contains("Prototype (@ContextPooled)"));
        assertTrue(html.contains("Pooled 3/4"));
        assertTrue(html.contains("src/dealer.py"));
        assertTrue(html.contains(RecordingContextCustomizer.class.getName()));
        assertTrue(html.contains("/pythonpool"));
    }

    @Test
    void detailNeverRendersMaskedOrEnvironmentValues() throws IOException {
        String html = renderDetail(populatedBody());

        assertFalse(html.contains("super-secret"));
        assertTrue(html.contains("API_KEY"));
    }

    @Test
    void detailRendersEmptyStatesWithoutFailing() throws IOException {
        String html = renderDetail(emptyBody());

        assertTrue(html.contains("no generated Python bean definitions were found"));
        assertTrue(html.contains("No customizers registered"));
        assertTrue(html.contains("No <code>fileslist.txt</code> metadata was found"));
        assertTrue(html.contains("Pool statistics are unavailable"));
        assertFalse(html.contains("Pooled "));
    }

    @Test
    void bodyRendersTheSummaryForBothStates() throws IOException {
        assertTrue(render("views/graalpy/body", Map.of("body", populatedBody())).contains("Pooled 3/4"));
        assertTrue(render("views/graalpy/body", Map.of("body", emptyBody())).contains("Unavailable"));
    }

    private String renderDetail(GraalPyControlPanel.Body body) throws IOException {
        return render("views/graalpy/detail", Map.of("controlPanel", Map.of("body", body)));
    }

    private String render(String view, Object model) throws IOException {
        return handlebars.compile(view).apply(model);
    }

    private static GraalPyControlPanel.Body populatedBody() {
        var module = new PythonBeanScanner.PythonBean(
            "dealerModule", "example.DealerModule", "module", "dealer", "cards", List.of(), "Singleton", "Default",
            "Not created",
            List.of(new PythonBeanScanner.MethodSignature("deal", "String", "String name, int count", "String deal(String name, int count)")));
        var pooled = new PythonBeanScanner.PythonBean(
            "pricingRules", "example.PricingRules", "class", "Pricing rules", "pricing", List.of("quote"),
            "Prototype (@ContextPooled)", "Default", "Per pooled context", List.of());
        var pool = new PythonRuntimeInspector.ContextPool(
            "Pooled 3/4", true, "",
            new PythonRuntimeInspector.PoolStatistics(true, 4, 3, 2, 1, 10, 2, 120, 90, false),
            new PythonRuntimeInspector.PoolSettings("true", "4", "PT5S", "2", "true", "true"),
            new PythonRuntimeInspector.EngineInfo(true, "GraalVM", "25.3.4.1"),
            List.of("Borrowers waited 2 time(s) for a context."));
        var configuration = new PythonRuntimeInspector.ContextConfiguration(
            true,
            List.of(
                new PythonRuntimeInspector.ConfigurationEntry("python.PythonPath", "/app/src", false),
                new PythonRuntimeInspector.ConfigurationEntry("python.AuthToken", "••••••", true)),
            List.of("java.util.*"),
            List.of("API_KEY"),
            List.of(RecordingContextCustomizer.class.getName()));
        var vfs = new GraalPyVfsMetadataReader.GraalPyVfsMetadata(
            List.of(GraalPyVfsMetadataReader.APPLICATION_ROOT, GraalPyVfsMetadataReader.LEGACY_ROOT),
            List.of(new GraalPyVfsMetadataReader.GraalPyVfsResource(
                "classpath resource #1 (file)", "file", GraalPyVfsMetadataReader.APPLICATION_ROOT, 1, false)),
            List.of(new GraalPyVfsMetadataReader.GraalPyVfsEntry("src/dealer.py", "src", "classpath resource #1 (file)")),
            List.of(), false, 0);
        return new GraalPyControlPanel.Body(true, List.of(module, pooled), pool, configuration,
            new PythonRuntimeInspector.RuntimeModules(true, true), vfs);
    }

    private static GraalPyControlPanel.Body emptyBody() {
        var pool = new PythonRuntimeInspector.ContextPool(
            "Unavailable", false, "The Python context executor reported no pool statistics.", null,
            new PythonRuntimeInspector.PoolSettings("Not configured", "Not configured", "Not configured",
                "Not configured", "Not configured", "Not configured"),
            new PythonRuntimeInspector.EngineInfo(false, "Unknown", "Unknown"),
            List.of());
        var configuration = new PythonRuntimeInspector.ContextConfiguration(false, List.of(), List.of(), List.of(), List.of());
        var vfs = new GraalPyVfsMetadataReader.GraalPyVfsMetadata(
            List.of(GraalPyVfsMetadataReader.APPLICATION_ROOT, GraalPyVfsMetadataReader.LEGACY_ROOT),
            List.of(), List.of(), List.of(), false, 0);
        return new GraalPyControlPanel.Body(true, List.of(), pool, configuration,
            new PythonRuntimeInspector.RuntimeModules(false, false), vfs);
    }
}
