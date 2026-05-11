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
package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.context.BeanContext;
import io.micronaut.controlpanel.panels.flyway.model.FlywayBody;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.extensibility.MigrationType;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlywayStatusServiceTest {

    @Test
    void mapsFlywayMigrationStatesToVisibleGroups() {
        assertEquals(FlywayStatusService.StateGroup.SUCCESS, FlywayStatusService.stateGroup(MigrationState.SUCCESS));
        assertEquals(FlywayStatusService.StateGroup.PENDING, FlywayStatusService.stateGroup(MigrationState.PENDING));
        assertEquals(FlywayStatusService.StateGroup.FAILED, FlywayStatusService.stateGroup(MigrationState.FAILED));
        assertEquals(FlywayStatusService.StateGroup.FUTURE, FlywayStatusService.stateGroup(MigrationState.FUTURE_SUCCESS));
        assertEquals(FlywayStatusService.StateGroup.MISSING, FlywayStatusService.stateGroup(MigrationState.MISSING_SUCCESS));
        assertEquals(FlywayStatusService.StateGroup.OUT_OF_ORDER, FlywayStatusService.stateGroup(MigrationState.OUT_OF_ORDER));
        assertEquals(FlywayStatusService.StateGroup.IGNORED, FlywayStatusService.stateGroup(MigrationState.IGNORED));
        assertEquals(FlywayStatusService.StateGroup.UNKNOWN, FlywayStatusService.stateGroup(null));
    }

    @Test
    void readsMultipleFlywayConfigurationsAndSummarizesStates() {
        Flyway defaultFlyway = flyway(
            migration("1", "create tables", MigrationState.SUCCESS, 1),
            migration("2", "add accounts", MigrationState.PENDING, null),
            migration("3", "broken migration", MigrationState.FAILED, 2)
        );
        Flyway reportingFlyway = flyway(
            migration("1", "baseline", MigrationState.SUCCESS, 1)
        );
        BeanContext beanContext = beanContext(Map.of(
            "default", defaultFlyway,
            "reporting", reportingFlyway
        ));

        FlywayBody body = new FlywayStatusService(beanContext, panelConfiguration(true, true)).getBody();

        assertEquals(2, body.totalConfigurations());
        assertEquals(4, body.totalMigrations());
        assertEquals(2, body.availableConfigurations());
        assertEquals(0, body.unavailableConfigurations());
        assertEquals("Failed", body.configurations().getFirst().statusLabel());
        assertEquals("3", body.configurations().getFirst().latestApplied());
        assertStateCount(body, "success", 2);
        assertStateCount(body, "pending", 1);
        assertStateCount(body, "failed", 1);
    }

    @Test
    void reportsUnavailableConfigurationWithoutFailingTheWholePanel() {
        Flyway failingFlyway = mock(Flyway.class);
        when(failingFlyway.info()).thenThrow(new IllegalStateException("metadata unavailable"));
        BeanContext beanContext = beanContext(Map.of("default", failingFlyway));

        FlywayBody body = new FlywayStatusService(beanContext, panelConfiguration(true, true)).getBody();

        assertEquals(1, body.totalConfigurations());
        assertEquals(0, body.totalMigrations());
        assertEquals(0, body.availableConfigurations());
        assertEquals(1, body.unavailableConfigurations());
        assertFalse(body.configurations().getFirst().available());
        assertEquals("Unavailable", body.configurations().getFirst().statusLabel());
        assertTrue(body.configurations().getFirst().message().contains("could not be read"));
    }

    @Test
    void reportsEmptyBodyWhenThereAreNoFlywayBeans() {
        BeanContext beanContext = beanContext(Map.of());

        FlywayBody body = new FlywayStatusService(beanContext, panelConfiguration(false, false)).getBody();

        assertFalse(body.hasConfigurations());
        assertEquals(0, body.totalConfigurations());
        assertEquals(0, body.totalMigrations());
        assertFalse(body.showChecksums());
        assertFalse(body.showInstalledBy());
    }

    private static void assertStateCount(FlywayBody body, String id, int count) {
        assertEquals(count, body.stateSummaries().stream()
            .filter(summary -> summary.id().equals(id))
            .findFirst()
            .orElseThrow()
            .count());
    }

    private static BeanContext beanContext(Map<String, Flyway> flyways) {
        BeanContext beanContext = mock(BeanContext.class);
        when(beanContext.mapOfType(Flyway.class)).thenReturn(new LinkedHashMap<>(flyways));
        return beanContext;
    }

    private static FlywayPanelConfiguration panelConfiguration(boolean showChecksums, boolean showInstalledBy) {
        FlywayPanelConfiguration configuration = mock(FlywayPanelConfiguration.class);
        when(configuration.isShowChecksums()).thenReturn(showChecksums);
        when(configuration.isShowInstalledBy()).thenReturn(showInstalledBy);
        return configuration;
    }

    private static Flyway flyway(MigrationInfo... migrations) {
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        MigrationInfo currentMigration = currentMigration(migrations);
        when(infoService.all()).thenReturn(migrations);
        when(infoService.current()).thenReturn(currentMigration);
        Flyway flyway = mock(Flyway.class);
        when(flyway.info()).thenReturn(infoService);
        return flyway;
    }

    private static MigrationInfo currentMigration(MigrationInfo[] migrations) {
        return List.of(migrations).stream()
            .filter(migration -> migration.getState() == MigrationState.SUCCESS)
            .reduce((first, second) -> second)
            .orElse(null);
    }

    private static MigrationInfo migration(String version, String description, MigrationState state, Integer installedRank) {
        MigrationInfo migration = mock(MigrationInfo.class);
        MigrationType type = mock(MigrationType.class);
        when(type.name()).thenReturn("SQL");
        when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        when(migration.getDescription()).thenReturn(description);
        when(migration.getState()).thenReturn(state);
        when(migration.getType()).thenReturn(type);
        when(migration.getScript()).thenReturn("V" + version + "__" + description.replace(' ', '_') + ".sql");
        when(migration.getInstalledRank()).thenReturn(installedRank);
        when(migration.getInstalledOn()).thenReturn(installedRank == null ? null : new Date(0));
        when(migration.getExecutionTime()).thenReturn(installedRank == null ? null : 15);
        when(migration.getChecksum()).thenReturn(1234);
        when(migration.getInstalledBy()).thenReturn("sa");
        return migration;
    }
}
