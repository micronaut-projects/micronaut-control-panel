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
package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiquibaseControlPanelTest {

    @Test
    void exposesDatasourceCategoryAndBadge() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "liquibase.datasources.audit.enabled", false
        ))) {
            LiquibaseControlPanel panel = context.getBean(LiquibaseControlPanel.class);

            assertEquals("liquibase", panel.getName());
            assertEquals("datasource", panel.getCategory().id());
            assertEquals("Data Sources", panel.getCategory().name());
            assertEquals("", panel.getBadge());
        }
    }
}
