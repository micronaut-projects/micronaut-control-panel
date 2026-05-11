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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.liquibase.LiquibaseConfigurationProperties;
import jakarta.inject.Singleton;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeLogHistoryService;
import liquibase.changelog.RanChangeSet;
import liquibase.changelog.StandardChangeLogHistoryService;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static io.micronaut.core.util.StringUtils.trimToNull;

/**
 * Reads Liquibase history without invoking Liquibase write operations.
 */
@Internal
@Singleton
@Requires(classes = LiquibaseConfigurationProperties.class)
public class LiquibaseHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseHistoryService.class);
    static final String READ_FAILURE_MESSAGE = "Unable to read Liquibase history. See debug logs for details.";

    private final Collection<LiquibaseConfigurationProperties> configurations;
    private final ApplicationContext applicationContext;
    private final DataSourceResolver dataSourceResolver;
    private final LiquibasePanelConfiguration panelConfiguration;

    public LiquibaseHistoryService(Collection<LiquibaseConfigurationProperties> configurations,
                                   ApplicationContext applicationContext,
                                   @Nullable DataSourceResolver dataSourceResolver,
                                   LiquibasePanelConfiguration panelConfiguration) {
        this.configurations = configurations;
        this.applicationContext = applicationContext;
        this.dataSourceResolver = dataSourceResolver != null ? dataSourceResolver : DataSourceResolver.DEFAULT;
        this.panelConfiguration = panelConfiguration;
    }

    /**
     * @return current Liquibase history view model
     */
    public LiquibasePanelBody getBody() {
        List<LiquibaseDataSourceHistory> histories = configurations.stream()
            .sorted(Comparator.comparing(LiquibaseConfigurationProperties::getNameQualifier))
            .map(this::readConfiguration)
            .toList();
        return new LiquibasePanelBody(histories, panelConfiguration.isShowChecksums(), panelConfiguration.isShowDeploymentIds());
    }

    private LiquibaseDataSourceHistory readConfiguration(LiquibaseConfigurationProperties configuration) {
        String name = configuration.getNameQualifier();
        if (!configuration.isEnabled()) {
            return LiquibaseDataSourceHistory.disabled(name);
        }

        return applicationContext.findBean(DataSource.class, Qualifiers.byName(name))
            .map(dataSource -> readHistory(configuration, dataSource))
            .orElseGet(() -> LiquibaseDataSourceHistory.error(name, "No datasource bean named '" + name + "' is available."));
    }

    private LiquibaseDataSourceHistory readHistory(LiquibaseConfigurationProperties configuration, DataSource dataSource) {
        String name = configuration.getNameQualifier();
        Connection connection = null;
        Database database = null;
        try {
            DataSource resolvedDataSource = dataSourceResolver.resolve(dataSource);
            connection = resolvedDataSource.getConnection();
            database = createDatabase(connection, configuration);
            ChangeLogHistoryService historyService = new StandardChangeLogHistoryService();
            historyService.setDatabase(database);
            List<LiquibaseChangeSet> changeSets = historyService
                .getRanChangeSets()
                .stream()
                .map(LiquibaseHistoryService::mapChangeSet)
                .sorted(Comparator
                    .comparing(LiquibaseChangeSet::orderExecuted, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(LiquibaseChangeSet::dateExecuted, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(LiquibaseChangeSet::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
            return LiquibaseDataSourceHistory.enabled(name, changeSets);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to read Liquibase history for datasource '{}'", name, e);
            }
            return LiquibaseDataSourceHistory.error(name, READ_FAILURE_MESSAGE);
        } finally {
            closeDatabase(database, connection);
        }
    }

    private static Database createDatabase(Connection connection, LiquibaseConfigurationProperties configuration) throws Exception {
        DatabaseConnection liquibaseConnection = new JdbcConnection(connection);
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(liquibaseConnection);

        String defaultSchema = configuration.getDefaultSchema();
        if (StringUtils.isNotEmpty(defaultSchema)) {
            if (database.supportsSchemas()) {
                database.setDefaultSchemaName(defaultSchema);
            } else if (database.supportsCatalogs()) {
                database.setDefaultCatalogName(defaultSchema);
            }
        }
        String liquibaseSchema = configuration.getLiquibaseSchema();
        if (StringUtils.isNotEmpty(liquibaseSchema)) {
            if (database.supportsSchemas()) {
                database.setLiquibaseSchemaName(liquibaseSchema);
            } else if (database.supportsCatalogs()) {
                database.setLiquibaseCatalogName(liquibaseSchema);
            }
        }
        if (trimToNull(configuration.getLiquibaseTablespace()) != null && database.supportsTablespaces()) {
            database.setLiquibaseTablespaceName(configuration.getLiquibaseTablespace());
        }
        if (trimToNull(configuration.getDatabaseChangeLogTable()) != null) {
            database.setDatabaseChangeLogTableName(configuration.getDatabaseChangeLogTable());
        }
        if (trimToNull(configuration.getDatabaseChangeLogLockTable()) != null) {
            database.setDatabaseChangeLogLockTableName(configuration.getDatabaseChangeLogLockTable());
        }
        return database;
    }

    private static LiquibaseChangeSet mapChangeSet(RanChangeSet changeSet) {
        CheckSum checkSum = changeSet.getLastCheckSum();
        return new LiquibaseChangeSet(
            changeSet.getOrderExecuted(),
            changeSet.getId(),
            changeSet.getAuthor(),
            changeSet.getChangeLog(),
            changeSet.getStoredChangeLog(),
            changeSet.getDescription(),
            changeSet.getComments(),
            changeSet.getExecType() == null ? null : changeSet.getExecType().name(),
            changeSet.getDateExecuted() == null ? null : Instant.ofEpochMilli(changeSet.getDateExecuted().getTime()).toString(),
            changeSet.getDeploymentId(),
            sorted(changeSet.getContextExpression() == null ? Set.of() : changeSet.getContextExpression().getContexts()),
            sorted(changeSet.getLabels() == null ? Set.of() : changeSet.getLabels().getLabels()),
            checkSum == null ? null : checkSum.toString(),
            changeSet.getTag());
    }

    private static List<String> sorted(Set<String> values) {
        return values == null ? List.of() : values.stream().sorted().toList();
    }

    private static void closeDatabase(@Nullable Database database, @Nullable Connection connection) {
        if (database != null) {
            try {
                database.close();
            } catch (Exception e) {
                LOG.debug("Error closing Liquibase database after history read", e);
            }
        } else if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.debug("Error closing JDBC connection after Liquibase history read", e);
            }
        }
    }
}
