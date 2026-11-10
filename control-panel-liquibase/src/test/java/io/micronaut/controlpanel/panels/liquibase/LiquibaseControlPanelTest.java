package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import liquibase.Scope;
import liquibase.changelog.ChangeSet;
import liquibase.executor.ExecutorService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
            assertEquals("", panel.getBadge());
            assertEquals("Change sets", panel.getDetailLinkName());
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
            assertEquals("disabled", legacy.summary());

            LiquibaseHistory orphan = histories.get(2);
            assertEquals(LiquibaseHistory.Status.UNREADABLE, orphan.status());
            assertEquals("badge-destructive", orphan.badgeClass());

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(LiquibaseControlPanel.NAME).isPresent());
        }
    }

    @Test
    void honoursTheConfiguredSchemasAndChangeLogTable() {
        Map<String, Object> properties = Map.of(
            "datasources.default.url", "jdbc:h2:mem:liquibasePanelSchemas;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS APP\\;CREATE SCHEMA IF NOT EXISTS AUDIT",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "datasources.default.password", "",
            "liquibase.datasources.default.change-log", "classpath:db/changelog.xml",
            "liquibase.datasources.default.default-schema", "APP",
            "liquibase.datasources.default.liquibase-schema", "AUDIT",
            "liquibase.datasources.default.database-change-log-table", "APP_CHANGELOG"
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            LiquibaseHistory history = context.getBean(LiquibaseInspector.class).inspect().getFirst();
            assertEquals(LiquibaseHistory.Status.READ, history.status());
            assertEquals(3, history.changeSets().size());
        }
    }

    @Test
    void historyIsUnreadableWhenTheDatasourceFails() {
        Map<String, Object> properties = Map.of(
            "spec.name", "FailingDataSourceResolver",
            "datasources.default.url", "jdbc:h2:mem:liquibasePanelFailing;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "datasources.default.password", "",
            "liquibase.datasources.default.change-log", "classpath:db/changelog.xml"
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            context.getBean(FailingDataSourceResolver.class).failing = true;
            LiquibaseHistory history = context.getBean(LiquibaseInspector.class).inspect().getFirst();
            assertEquals(LiquibaseHistory.Status.UNREADABLE, history.status());
            assertTrue(history.changeSets().isEmpty());
            assertEquals("classpath:db/changelog.xml", history.changeLog());
        }
    }

    @Test
    void theConnectionIsReleasedWhenProbingTheDatabaseFails() throws Exception {
        Map<String, Object> properties = Map.of(
            "spec.name", "FailingDataSourceResolver",
            "datasources.default.url", "jdbc:h2:mem:liquibasePanelProbe;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "datasources.default.password", "",
            "liquibase.datasources.default.change-log", "classpath:db/changelog.xml"
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            FailingDataSourceResolver resolver = context.getBean(FailingDataSourceResolver.class);
            resolver.probeFailing = true;
            LiquibaseHistory history = context.getBean(LiquibaseInspector.class).inspect().getFirst();
            assertEquals(LiquibaseHistory.Status.UNREADABLE, history.status());
            assertEquals(1, resolver.connections.size());
            assertTrue(resolver.connections.getFirst().isClosed(), "the connection goes back to the pool");
        }
    }

    @Test
    void readingTheHistoryDoesNotKeepLiquibaseExecutors() throws Exception {
        Map<String, Object> properties = Map.of(
            "datasources.default.url", "jdbc:h2:mem:liquibasePanelExecutors;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "datasources.default.password", "",
            "liquibase.datasources.default.change-log", "classpath:db/changelog.xml"
        );
        try (ApplicationContext context = ApplicationContext.run(properties)) {
            LiquibaseInspector inspector = context.getBean(LiquibaseInspector.class);
            assertEquals(LiquibaseHistory.Status.READ, inspector.inspect().getFirst().status());
            int executors = executorCount();
            assertEquals(LiquibaseHistory.Status.READ, inspector.inspect().getFirst().status());
            assertEquals(LiquibaseHistory.Status.READ, inspector.inspect().getFirst().status());
            assertEquals(executors, executorCount(), "each read clears the executor it created");
        }
    }

    private static int executorCount() throws ReflectiveOperationException {
        ExecutorService executorService = Scope.getCurrentScope().getSingleton(ExecutorService.class);
        Field field = ExecutorService.class.getDeclaredField("executors");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(executorService)).size();
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
