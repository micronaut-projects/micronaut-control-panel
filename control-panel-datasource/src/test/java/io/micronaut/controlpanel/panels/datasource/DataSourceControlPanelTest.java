/*
 * Copyright 2017-2025 original authors
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

package io.micronaut.controlpanel.panels.datasource;

import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.datasource.model.Body;
import io.micronaut.controlpanel.panels.datasource.model.DataSourceInfo;
import io.micronaut.controlpanel.panels.datasource.model.DatabaseType;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import io.micronaut.controlpanel.panels.datasource.model.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSourceControlPanelTest {

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private Environment environment;

    @Mock
    private ControlPanelConfiguration configuration;

    private static final String BEAN_NAME = "testDataSource";
    private static final List<Table> EMPTY_TABLE_LIST = List.of();
    private static final String MERMAID_ER = "erDiagram\n  TABLE1 ||--o{ TABLE2 : \"has\"";

    @BeforeEach
    void setUp() {
        lenient().when(environment.getProperty(DataSourceControlPanel.DATASOURCE_ENABLED_PROPERTY.formatted(BEAN_NAME), Boolean.class, Boolean.TRUE)).thenReturn(true);
    }

    @Test
    void constructorInitializesFieldsCorrectly() {
        // Given
        when(dataSourceService.getTables()).thenReturn(EMPTY_TABLE_LIST);
        when(dataSourceService.generateMermaidER(EMPTY_TABLE_LIST)).thenReturn(MERMAID_ER);
        when(environment.getProperty("datasources." + BEAN_NAME + ".url", String.class, "")).thenReturn("jdbc:h2:mem:test");
        when(environment.getProperty("datasources." + BEAN_NAME + ".username", String.class, "")).thenReturn("sa");
        when(environment.getProperty("datasources." + BEAN_NAME + ".password", String.class, "")).thenReturn("");
        when(environment.getProperty("datasources." + BEAN_NAME + ".dialect", String.class, "")).thenReturn("H2");
        when(environment.getProperty("datasources." + BEAN_NAME + ".db-type", String.class, "")).thenReturn("");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);

        // Then
        assertEquals(BEAN_NAME, panel.getBeanName());
        assertEquals(DataSourceControlPanel.NAME, panel.getPanelName());
        assertEquals("Detail", panel.getDetailLinkName());
        assertNotNull(panel.getBody());
        verify(dataSourceService).getTables();
        verify(dataSourceService).generateMermaidER(EMPTY_TABLE_LIST);
    }

    @Test
    void createDataSourceInfoExtractsPropertiesCorrectly() {
        // Given
        String expectedUrl = "jdbc:postgresql://localhost:5432/test";
        String expectedUsername = "user";
        String expectedPassword = "pass";
        String expectedDialect = "POSTGRES";
        String expectedDbType = "postgres";
        when(environment.getProperty("datasources." + BEAN_NAME + ".url", String.class, "")).thenReturn(expectedUrl);
        when(environment.getProperty("datasources." + BEAN_NAME + ".username", String.class, "")).thenReturn(expectedUsername);
        when(environment.getProperty("datasources." + BEAN_NAME + ".password", String.class, "")).thenReturn(expectedPassword);
        when(environment.getProperty("datasources." + BEAN_NAME + ".dialect", String.class, "")).thenReturn(expectedDialect);
        when(environment.getProperty("datasources." + BEAN_NAME + ".db-type", String.class, "")).thenReturn(expectedDbType);

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);
        DataSourceInfo info = panel.getBody().dataSourceInfo();

        // Then
        assertEquals(BEAN_NAME, info.name());
        assertEquals(expectedUrl, info.jdbcUrl());
        assertEquals(expectedUsername, info.username());
        assertEquals(expectedPassword, info.password());
        assertEquals(DatabaseType.POSTGRES, info.type());
    }

    @Test
    void constructorDoesNotInspectDisabledDatasource() {
        // Given
        when(environment.getProperty(DataSourceControlPanel.DATASOURCE_ENABLED_PROPERTY.formatted(BEAN_NAME), Boolean.class, Boolean.TRUE)).thenReturn(false);
        when(environment.getProperty(anyString(), eq(String.class), eq(""))).thenReturn("");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);

        // Then
        assertFalse(panel.isEnabled());
        assertEquals(EMPTY_TABLE_LIST, panel.getBody().tables());
        assertEquals("", panel.getBody().mermaidEr());
        verify(dataSourceService, never()).getTables();
        verify(dataSourceService, never()).getJdbcInfo();
        verify(dataSourceService, never()).getPoolInfo();
        verify(dataSourceService, never()).generateMermaidER(any());
    }

    @Test
    void getBodyReturnsExpectedBody() {
        // Given
        when(dataSourceService.getTables()).thenReturn(EMPTY_TABLE_LIST);
        when(dataSourceService.generateMermaidER(EMPTY_TABLE_LIST)).thenReturn(MERMAID_ER);
        when(environment.getProperty(anyString(), eq(String.class), eq(""))).thenReturn("");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);
        Body body = panel.getBody();

        // Then
        assertNotNull(body);
        assertEquals(EMPTY_TABLE_LIST, body.tables());
        assertEquals(MERMAID_ER, body.mermaidEr());
        assertNotNull(body.dataSourceInfo());
    }

    @Test
    void getBodyIncludesPoolInfoWhenAvailable() {
        // Given
        var poolInfo = new PoolInfo(
            "HikariCP",
            "main-pool",
            "com.zaxxer.hikari.HikariDataSource",
            PoolInfo.PoolStats.of(1, 2, 3, 10, 1, 0),
            List.of()
        );
        when(dataSourceService.getTables()).thenReturn(EMPTY_TABLE_LIST);
        when(dataSourceService.generateMermaidER(EMPTY_TABLE_LIST)).thenReturn(MERMAID_ER);
        when(dataSourceService.getPoolInfo()).thenReturn(Optional.of(poolInfo));
        when(environment.getProperty(anyString(), eq(String.class), eq(""))).thenReturn("");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);

        // Then
        assertSame(poolInfo, panel.getBody().poolInfo());
    }

    @Test
    void getBadgeIsEmptyWhenTableCountIsRenderedInTheBody() {
        // Given
        List<Table> tables = List.of(new Table("", "test", List.of(), Set.of(), List.of()));
        when(dataSourceService.getTables()).thenReturn(tables);
        when(environment.getProperty(anyString(), eq(String.class), eq(""))).thenReturn("");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);

        // Then
        assertEquals("", panel.getBadge());
    }

    @Test
    void getIconReturnsCorrectIconForDatabaseType() {
        // Given
        when(dataSourceService.getTables()).thenReturn(EMPTY_TABLE_LIST);
        when(dataSourceService.generateMermaidER(EMPTY_TABLE_LIST)).thenReturn(MERMAID_ER);
        when(environment.getProperty("datasources." + BEAN_NAME + ".dialect", String.class, "")).thenReturn("POSTGRES");
        when(environment.getProperty("datasources." + BEAN_NAME + ".url", String.class, "")).thenReturn("url");
        when(environment.getProperty("datasources." + BEAN_NAME + ".username", String.class, "")).thenReturn("user");
        when(environment.getProperty("datasources." + BEAN_NAME + ".password", String.class, "")).thenReturn("pass");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);

        // Then
        assertEquals("si si-postgresql", panel.getIcon());

        // Test default
        when(environment.getProperty("datasources." + BEAN_NAME + ".dialect", String.class, "")).thenReturn("UNKNOWN");
        when(environment.getProperty("datasources." + BEAN_NAME + ".db-type", String.class, "")).thenReturn("");
        DataSourceControlPanel defaultPanel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);
        assertEquals(DataSourceControlPanel.DEFAULT_ICON_CLASS, defaultPanel.getIcon());
    }

    @Test
    void getCategoryReturnsExpectedCategory() {
        // Given
        when(dataSourceService.getTables()).thenReturn(EMPTY_TABLE_LIST);
        when(dataSourceService.generateMermaidER(EMPTY_TABLE_LIST)).thenReturn(MERMAID_ER);
        when(environment.getProperty(anyString(), eq(String.class), eq(""))).thenReturn("");

        // When
        DataSourceControlPanel panel = new DataSourceControlPanel(BEAN_NAME, dataSourceService, environment, configuration);
        var category = panel.getCategory();

        // Then
        assertEquals(DataSourceControlPanel.NAME, category.id());
        assertEquals("Data Sources", category.name());
        assertEquals(DataSourceControlPanel.DEFAULT_ICON_CLASS, category.iconClass());
    }
}
