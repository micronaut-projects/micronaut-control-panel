package io.micronaut.controlpanel.panels.flyway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayControlPanelTest {

    private static final Map<String, Object> PROPERTIES = Map.of(
        "datasources.default.url", "jdbc:h2:mem:flywayPanelTest;DB_CLOSE_DELAY=-1",
        "datasources.default.driver-class-name", "org.h2.Driver",
        "datasources.default.username", "sa",
        "datasources.default.password", "",
        "datasources.legacy.url", "jdbc:h2:mem:flywayPanelLegacy;DB_CLOSE_DELAY=-1",
        "datasources.legacy.driver-class-name", "org.h2.Driver",
        "datasources.legacy.username", "sa",
        "datasources.legacy.password", "",
        "flyway.datasources.default.enabled", true,
        "flyway.datasources.legacy.enabled", false
    );

    @Test
    void showsTheMigrationHistoryOfEachConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            FlywayControlPanel panel = context.getBean(FlywayControlPanel.class);
            assertEquals("Flyway", panel.getTitle());
            assertEquals("datasource", panel.getCategory().id());

            List<FlywaySchema> schemas = panel.getBody().schemas();
            assertEquals(List.of("default", "legacy"), schemas.stream().map(FlywaySchema::name).toList());

            FlywaySchema schema = schemas.getFirst();
            assertEquals(FlywaySchema.Status.READ, schema.status());
            assertEquals("2", schema.currentVersion());
            assertEquals(2, schema.applied());
            assertEquals(0, schema.pending());
            assertEquals(0, schema.failed());
            assertEquals("up to date", schema.summary());
            assertNull(schema.statusMessage());

            FlywayMigration first = schema.migrations().getFirst();
            assertEquals("1", first.version());
            assertEquals("create book", first.description());
            assertEquals("V1__create_book.sql", first.script());
            assertEquals("Success", first.state());
            assertEquals("SQL", first.type());
            assertNotNull(first.installedOn());
            assertNotNull(first.executionTime());

            FlywaySchema legacy = schemas.get(1);
            assertEquals(FlywaySchema.Status.DISABLED, legacy.status());
            assertEquals("disabled", legacy.summary());
            assertTrue(legacy.migrations().isEmpty());
            assertNotNull(legacy.statusMessage());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(FlywayControlPanel.NAME).isPresent());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(FlywayControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(FlywayControlPanel.class));
        }
    }

    @Test
    void summaryHighlightsFailedAndPendingMigrations() {
        FlywaySchema failed = new FlywaySchema("a", FlywaySchema.Status.READ, "1", List.of(), 1, 1, 1);
        assertEquals("1 failed", failed.summary());
        assertEquals("badge-destructive", failed.badgeClass());

        FlywaySchema pending = new FlywaySchema("b", FlywaySchema.Status.READ, "1", List.of(), 1, 2, 0);
        assertEquals("2 pending", pending.summary());
        assertEquals("badge-primary", pending.badgeClass());

        FlywaySchema unreadable = FlywaySchema.withoutMigrations("c", FlywaySchema.Status.UNREADABLE);
        assertEquals("unavailable", unreadable.summary());
        assertEquals("badge-destructive", unreadable.badgeClass());
    }

    @Test
    void migrationStatesMapToBadges() {
        assertEquals("badge-destructive", FlywayInspector.badgeClass(MigrationState.FAILED));
        assertEquals("badge-destructive", FlywayInspector.badgeClass(MigrationState.MISSING_FAILED));
        assertEquals("badge-primary", FlywayInspector.badgeClass(MigrationState.PENDING));
        assertEquals("badge-outline", FlywayInspector.badgeClass(MigrationState.MISSING_SUCCESS));
        assertEquals("badge-outline", FlywayInspector.badgeClass(MigrationState.FUTURE_SUCCESS));
        assertEquals("badge-secondary", FlywayInspector.badgeClass(MigrationState.SUCCESS));
        assertEquals("badge-secondary", FlywayInspector.badgeClass(null));
    }
}
