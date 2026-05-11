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
import io.micronaut.context.Qualifier;
import io.micronaut.liquibase.LiquibaseConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class LiquibaseHistoryServiceTest {

    @Test
    void bodyReportsNoConfigurations() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "datasources.default.url", "jdbc:h2:mem:noConfig;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa"
        ))) {
            LiquibasePanelBody body = context.getBean(LiquibaseHistoryService.class).getBody();

            assertFalse(body.hasConfigurations());
            assertEquals(0, body.totalChangeSets());
        }
    }

    @Test
    void disabledConfigurationDoesNotRequireDatasource() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "liquibase.datasources.audit.enabled", false,
            "liquibase.datasources.audit.change-log", "classpath:db/changelog/liquibase-panel.xml"
        ))) {
            LiquibasePanelBody body = context.getBean(LiquibaseHistoryService.class).getBody();

            assertTrue(body.hasConfigurations());
            assertEquals(1, body.dataSources().size());
            assertEquals("audit", body.dataSources().get(0).name());
            assertTrue(body.dataSources().get(0).isDisabled());
            assertEquals(0, body.totalChangeSets());
        }
    }

    @Test
    void enabledConfigurationWithMissingDatasourceReportsError() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "liquibase.datasources.missing.change-log", "classpath:db/changelog/liquibase-panel.xml"
        ))) {
            LiquibasePanelBody body = context.getBean(LiquibaseHistoryService.class).getBody();

            assertEquals(1, body.errorCount());
            assertTrue(body.dataSources().get(0).isError());
            assertTrue(body.dataSources().get(0).errorMessage().contains("No datasource bean named 'missing'"));
        }
    }

    @Test
    void enabledConfigurationReadsAppliedHistory() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "datasources.default.url", "jdbc:h2:mem:liquibasePanel;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "liquibase.datasources.default.change-log", "classpath:db/changelog/liquibase-panel.xml"
        ))) {
            LiquibasePanelBody body = context.getBean(LiquibaseHistoryService.class).getBody();

            assertTrue(body.hasConfigurations());
            assertEquals(1, body.enabledCount());
            assertEquals(2, body.totalChangeSets());
            LiquibaseDataSourceHistory history = body.dataSources().get(0);
            assertEquals("default", history.name());
            assertEquals("enabled", history.state());
            assertNotNull(history.latestExecutionDate());
            assertNotNull(history.latestDeploymentId());
            assertEquals("panel-test", history.latestTag());

            LiquibaseChangeSet createTable = history.changeSets().get(0);
            assertEquals("001-create-liquibase-panel-table", createTable.id());
            assertEquals("control-panel", createTable.author());
            assertEquals("EXECUTED", createTable.execType());
            assertTrue(createTable.contexts().contains("dev"));
            assertTrue(createTable.labels().contains("core"));
            assertNotNull(createTable.checksum());
        }
    }

    @Test
    void repeatedHistoryReadsDoNotAccumulateFactoryCachedServices() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "datasources.default.url", "jdbc:h2:mem:liquibasePanelCache;DB_CLOSE_DELAY=-1",
            "datasources.default.driver-class-name", "org.h2.Driver",
            "datasources.default.username", "sa",
            "liquibase.datasources.default.change-log", "classpath:db/changelog/liquibase-panel.xml"
        ))) {
            LiquibaseHistoryService service = context.getBean(LiquibaseHistoryService.class);
            int factoryServiceCount = changeLogHistoryFactoryServiceCount();

            service.getBody();
            service.getBody();

            assertEquals(factoryServiceCount, changeLogHistoryFactoryServiceCount());
        }
    }

    @Test
    void readFailureDoesNotExposeRawExceptionDetails() throws Exception {
        LiquibaseConfigurationProperties configuration = new LiquibaseConfigurationProperties("leaky");
        configuration.setEnabled(true);
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        Mockito.when(applicationContext.findBean(eq(DataSource.class), any(Qualifier.class)))
            .thenReturn(Optional.of(dataSource));
        Mockito.when(dataSource.getConnection())
            .thenThrow(new SQLException("jdbc:postgresql://db.example.test/internal?user=admin&password=secret"));
        LiquibaseHistoryService service = new LiquibaseHistoryService(
            List.of(configuration),
            applicationContext,
            null,
            new LiquibasePanelConfiguration());

        LiquibaseDataSourceHistory history = service.getBody().dataSources().get(0);

        assertTrue(history.isError());
        assertEquals(LiquibaseHistoryService.READ_FAILURE_MESSAGE, history.errorMessage());
        assertFalse(history.errorMessage().contains("jdbc:postgresql"));
        assertFalse(history.errorMessage().contains("db.example.test"));
        assertFalse(history.errorMessage().contains("admin"));
        assertFalse(history.errorMessage().contains("secret"));
    }

    @Test
    void visibilityFlagsAreConfigurationDriven() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.control-panel.panels.liquibase.show-checksums", false,
            "micronaut.control-panel.panels.liquibase.show-deployment-ids", false
        ))) {
            LiquibasePanelBody body = context.getBean(LiquibaseHistoryService.class).getBody();

            assertFalse(body.showChecksums());
            assertFalse(body.showDeploymentIds());
        }
    }

    private static int changeLogHistoryFactoryServiceCount() throws Exception {
        Object factory = liquibase.changelog.ChangeLogHistoryServiceFactory.getInstance();
        Field services = factory.getClass().getDeclaredField("services");
        services.setAccessible(true);
        return ((Map<?, ?>) services.get(factory)).size();
    }
}
