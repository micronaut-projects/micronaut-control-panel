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
package io.micronaut.controlpanel.panels.mongodb;

import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDbControlPanelTest {

    @Test
    void controlPanelUsesStableModeQualifiedName() {
        var panel = new MongoDbControlPanel(new StubService(), new ControlPanelConfiguration(MongoDbControlPanel.NAME));

        assertEquals("mongodb-sync-default", panel.getName());
        assertEquals("sync default", panel.getTitle());
        assertEquals("MongoDB", panel.getCategory().name());
        assertTrue(panel.getBody().client().buildOk());
    }

    private static final class StubService implements MongoDbDiagnosticService {
        @Override
        public String beanName() {
            return "default";
        }

        @Override
        public String mode() {
            return "sync";
        }

        @Override
        public MongoDbModels.Body getBody() {
            return new MongoDbModels.Body(
                new MongoDbModels.ClientSummary("default", "sync", "demo", List.of("localhost:27017"), Map.of(), "", "", "", "", "", false, 0, 0, 0, 0, 0, "", "8.0.0", true),
                List.of(),
                List.of(),
                false,
                false
            );
        }
    }
}
