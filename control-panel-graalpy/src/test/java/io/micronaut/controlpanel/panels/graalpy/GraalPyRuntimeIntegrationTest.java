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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.python.PythonContextExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the panel against Core's real GraalPy runtime instead of a stand-in executor.
 *
 * <p>Disabled by default: it boots a real polyglot engine and context, which downloads and starts the full GraalPy
 * runtime and is far slower than the rest of the suite. Run it locally with:</p>
 *
 * <pre>./gradlew :micronaut-control-panel-graalpy:test -Dgraalpy.integration=true --tests '*GraalPyRuntimeIntegrationTest*'</pre>
 */
@EnabledIfSystemProperty(named = "graalpy.integration", matches = "true")
class GraalPyRuntimeIntegrationTest {

    @Test
    void readsTheRealPoolSnapshotWithoutBorrowingAContext() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "GraalPyRuntimeIntegrationTest"))) {
            assertNotNull(context.getBean(PythonContextExecutor.class));

            GraalPyControlPanel panel = context.getBean(GraalPyControlPanel.class);
            long borrowsBefore = context.getBean(PythonContextExecutor.class).statistics().borrows();

            GraalPyControlPanel.Body body = panel.getBody();

            assertTrue(body.pool().available());
            assertTrue(body.configuration().available());
            assertTrue(body.vfs().hasResources());
            assertEquals(borrowsBefore, context.getBean(PythonContextExecutor.class).statistics().borrows());
        }
    }
}
