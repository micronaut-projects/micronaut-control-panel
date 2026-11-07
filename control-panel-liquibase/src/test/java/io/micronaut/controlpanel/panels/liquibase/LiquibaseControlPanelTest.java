package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import liquibase.changelog.ChangeSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibaseControlPanelTest {

    private static final Map<String, Object> PROPERTIES = Map.ofEntries(
        Map.entry("datasources.default.url", "jdbc:h2:mem:liquibasePanelTest;DB_CLOSE_DELAY=-1"),
        Map.entry("datasources.default.driver-class-name", "org.h2.Driver"),
        Map.entry("datasources.default.username", "sa"),
        Map.entry("datasources.default.password", ""),
        Map.entry("datasources.legacy.url", "jdbc:h2:mem:liquibasePanelLegacy;DB_CLOSE_DELAY=-1"),
        Map.entry("datasources.legacy.driver-class-name", "org.h2.Driver"),
        Map.entry("datasources.legacy.username", "sa"),
        Map.entry("datasources.legacy.password", ""),
        Map.entry("liquibase.datasources.default.change-log", "classpath:db/changelog.xml"),
        Map.entry("liquibase.datasources.legacy.change-log", "classpath:db/changelog.xml"),
        Map.entry("liquibase.datasources.legacy.enabled", false),
        Map.entry("liquibase.datasources.orphan.change-log", "classpath:db/changelog.xml"),
        Map.entry("liquibase.datasources.orphan.enabled", true)
    );

    @Test
    void showsTheChangeLogHistoryOfEachConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            LiquibaseControlPanel panel = context.getBean(LiquibaseControlPanel.class);
            assertEquals("Liquibase", panel.getTitle());
            assertEquals("datasource", panel.getCategory().id());

            List<LiquibaseHistory> histories = panel.getBody().histories();
            assertEquals(List.of("default", "legacy", "orphan"), histories.stream().map(LiquibaseHistory::name).toList());

            LiquibaseHistory history = histories.getFirst();
            assertEquals(LiquibaseHistory.Status.READ, history.status());
            assertEquals("classpath:db/changelog.xml", history.changeLog());
            assertEquals(3, history.changeSets().size());
            assertEquals(0, history.failed());
            assertEquals("1.0", history.lastTag());
            assertEquals("3 ran", history.summary());
            assertNull(history.statusMessage());

            LiquibaseChangeSet first = history.changeSets().getFirst();
            assertEquals(1, first.orderExecuted());
            assertEquals("create-book", first.id());
            assertEquals("alvaro", first.author());
            assertEquals("EXECUTED", first.execType());
            assertEquals("Books table", first.comments());
            assertEquals(List.of("catalog"), first.labels());
            assertNotNull(first.dateExecuted());
            assertEquals(List.of("dev"), history.changeSets().get(2).contexts());

            LiquibaseHistory legacy = histories.get(1);
            assertEquals(LiquibaseHistory.Status.DISABLED, legacy.status());
            assertTrue(legacy.changeSets().isEmpty());

            LiquibaseHistory orphan = histories.get(2);
            assertEquals(LiquibaseHistory.Status.UNREADABLE, orphan.status());
            assertEquals("badge-destructive", orphan.badgeClass());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(LiquibaseControlPanel.NAME).isPresent());
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(LiquibaseControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(LiquibaseControlPanel.class));
        }
    }

    @Test
    void executionTypesMapToBadges() {
        assertEquals("badge-destructive", LiquibaseInspector.badgeClass(ChangeSet.ExecType.FAILED));
        assertEquals("badge-outline", LiquibaseInspector.badgeClass(ChangeSet.ExecType.MARK_RAN));
        assertEquals("badge-secondary", LiquibaseInspector.badgeClass(ChangeSet.ExecType.EXECUTED));
        assertEquals("badge-secondary", LiquibaseInspector.badgeClass(null));
    }

    @Test
    void summaryHighlightsFailedChangeSets() {
        LiquibaseHistory failed = new LiquibaseHistory("a", null, LiquibaseHistory.Status.READ, List.of(), 2, null);
        assertEquals("2 failed", failed.summary());
        assertEquals("badge-destructive", failed.badgeClass());
    }
}
