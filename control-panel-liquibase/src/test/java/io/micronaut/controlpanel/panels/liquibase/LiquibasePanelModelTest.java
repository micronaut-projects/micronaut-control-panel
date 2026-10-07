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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibasePanelModelTest {

    @Test
    void emptyHistoryStateIsExplicit() {
        LiquibaseDataSourceHistory history = LiquibaseDataSourceHistory.enabled("audit", List.of());

        assertEquals("audit", history.name());
        assertEquals("empty", history.state());
        assertEquals("Empty history", history.stateLabel());
        assertEquals("info", history.badgeClass());
        assertEquals(0, history.count());
        assertFalse(history.hasChangeSets());
        assertFalse(history.isDisabled());
        assertFalse(history.isError());
        assertTrue(history.isEmptyHistory());
        assertNull(history.errorMessage());
        assertNull(history.latestExecutionDate());
        assertNull(history.latestDeploymentId());
        assertNull(history.latestTag());
    }

    @Test
    void panelBodySummarizesHistoryStates() {
        LiquibasePanelBody body = new LiquibasePanelBody(List.of(
            LiquibaseDataSourceHistory.enabled("default", List.of(changeSet(1, "2026-05-10T00:00:00Z", "first"))),
            LiquibaseDataSourceHistory.enabled("empty", List.of()),
            LiquibaseDataSourceHistory.disabled("disabled"),
            LiquibaseDataSourceHistory.error("broken", "Unable to read Liquibase history. See debug logs for details.")
        ), true, false);

        assertTrue(body.hasConfigurations());
        assertEquals(1, body.totalChangeSets());
        assertEquals(2, body.enabledCount());
        assertEquals(1, body.disabledCount());
        assertEquals(1, body.errorCount());
        assertTrue(body.showChecksums());
        assertFalse(body.showDeploymentIds());
    }

    @Test
    void latestHistoryValuesComeFromHighestExecutedChangeSet() {
        LiquibaseDataSourceHistory history = LiquibaseDataSourceHistory.enabled("default", List.of(
            changeSet(1, "2026-05-10T00:00:00Z", "first"),
            changeSet(2, "2026-05-11T00:00:00Z", "latest")
        ));

        assertEquals("enabled", history.state());
        assertEquals("OK", history.stateLabel());
        assertEquals("success", history.badgeClass());
        assertTrue(history.hasChangeSets());
        assertEquals(2, history.count());
        assertEquals("2026-05-11T00:00:00Z", history.latestExecutionDate());
        assertEquals("deployment-2", history.latestDeploymentId());
        assertEquals("latest", history.latestTag());
    }

    @Test
    void panelConfigurationSettersControlVisibility() {
        LiquibasePanelConfiguration configuration = new LiquibasePanelConfiguration();

        assertTrue(configuration.isShowChecksums());
        assertTrue(configuration.isShowDeploymentIds());

        configuration.setShowChecksums(false);
        configuration.setShowDeploymentIds(false);

        assertFalse(configuration.isShowChecksums());
        assertFalse(configuration.isShowDeploymentIds());
    }

    private static LiquibaseChangeSet changeSet(int orderExecuted, String dateExecuted, String tag) {
        return new LiquibaseChangeSet(
            orderExecuted,
            "change-" + orderExecuted,
            "control-panel",
            "classpath:db/changelog.xml",
            "db/changelog.xml",
            "description",
            "comments",
            "EXECUTED",
            dateExecuted,
            "deployment-" + orderExecuted,
            List.of("dev"),
            List.of("core"),
            "9:checksum",
            tag);
    }
}
