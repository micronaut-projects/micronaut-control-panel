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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.liquibase.LiquibaseConfigurationProperties;
import jakarta.inject.Singleton;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.RanChangeSet;
import liquibase.changelog.StandardChangeLogHistoryService;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the change log history of each Liquibase configuration from its {@code DATABASECHANGELOG} table, like the
 * {@code /liquibase} management endpoint does.
 *
 * <p>Nothing is updated, rolled back or tagged. If the history table does not exist yet, the history is empty.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = LiquibaseConfigurationProperties.class)
public class LiquibaseInspector {

    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseInspector.class);
    private static final DateTimeFormatter DATE_EXECUTED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public LiquibaseInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return one entry per Liquibase configuration, sorted by name
     */
    public List<LiquibaseHistory> inspect() {
        return beanContext.getBeansOfType(LiquibaseConfigurationProperties.class).stream()
            .sorted(Comparator.comparing(LiquibaseConfigurationProperties::getNameQualifier))
            .map(this::inspect)
            .toList();
    }

    private LiquibaseHistory inspect(LiquibaseConfigurationProperties configuration) {
        String name = configuration.getNameQualifier();
        String changeLog = configuration.getChangeLog();
        if (!configuration.isEnabled()) {
            return LiquibaseHistory.withoutChangeSets(name, changeLog, LiquibaseHistory.Status.DISABLED);
        }
        Optional<DataSource> dataSource = beanContext.findBean(DataSource.class, Qualifiers.byName(name));
        if (dataSource.isEmpty()) {
            LOG.debug("No datasource named '{}' for the Liquibase configuration of the same name", name);
            return LiquibaseHistory.withoutChangeSets(name, changeLog, LiquibaseHistory.Status.UNREADABLE);
        }
        try {
            DataSource resolved = beanContext.findBean(DataSourceResolver.class)
                .map(resolver -> resolver.resolve(dataSource.get()))
                .orElse(dataSource.get());
            return history(name, changeLog, readRanChangeSets(resolved, configuration));
        } catch (Exception e) {
            // The exception can carry the JDBC URL, so it is only logged
            LOG.debug("Unable to read the Liquibase history of '{}'", name, e);
            return LiquibaseHistory.withoutChangeSets(name, changeLog, LiquibaseHistory.Status.UNREADABLE);
        }
    }

    private static List<RanChangeSet> readRanChangeSets(DataSource dataSource, LiquibaseConfigurationProperties configuration) throws Exception {
        // Kept in a local so the connection goes back to the pool even when probing the database fails
        JdbcConnection connection = new JdbcConnection(dataSource.getConnection());
        Database database = null;
        try {
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(connection);
            configure(database, configuration);
            StandardChangeLogHistoryService history = new StandardChangeLogHistoryService();
            history.setDatabase(database);
            return history.getRanChangeSets();
        } finally {
            try {
                if (database != null) {
                    // Restores the auto-commit mode and closes the connection
                    database.close();
                }
            } finally {
                if (!connection.isClosed()) {
                    connection.close();
                }
            }
        }
    }

    private static void configure(Database database, LiquibaseConfigurationProperties configuration) throws DatabaseException {
        if (StringUtils.isNotEmpty(configuration.getDefaultSchema())) {
            if (database.supportsSchemas()) {
                database.setDefaultSchemaName(configuration.getDefaultSchema());
            } else if (database.supportsCatalogs()) {
                database.setDefaultCatalogName(configuration.getDefaultSchema());
            }
        }
        if (StringUtils.isNotEmpty(configuration.getLiquibaseSchema())) {
            if (database.supportsSchemas()) {
                database.setLiquibaseSchemaName(configuration.getLiquibaseSchema());
            } else if (database.supportsCatalogs()) {
                database.setLiquibaseCatalogName(configuration.getLiquibaseSchema());
            }
        }
        if (StringUtils.isNotEmpty(configuration.getDatabaseChangeLogTable())) {
            database.setDatabaseChangeLogTableName(configuration.getDatabaseChangeLogTable());
        }
    }

    static LiquibaseHistory history(String name, @Nullable String changeLog, List<RanChangeSet> ranChangeSets) {
        List<LiquibaseChangeSet> changeSets = ranChangeSets.stream()
            .sorted(Comparator.comparing(RanChangeSet::getOrderExecuted, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(LiquibaseInspector::changeSet)
            .toList();
        String lastTag = null;
        for (LiquibaseChangeSet changeSet : changeSets) {
            if (changeSet.tag() != null) {
                lastTag = changeSet.tag();
            }
        }
        long failed = ranChangeSets.stream().filter(changeSet -> changeSet.getExecType() == ChangeSet.ExecType.FAILED).count();
        return new LiquibaseHistory(name, changeLog, LiquibaseHistory.Status.READ, changeSets, failed, lastTag);
    }

    private static LiquibaseChangeSet changeSet(RanChangeSet changeSet) {
        ChangeSet.ExecType execType = changeSet.getExecType();
        return new LiquibaseChangeSet(
            changeSet.getOrderExecuted(),
            Objects.toString(changeSet.getId(), ""),
            Objects.toString(changeSet.getAuthor(), ""),
            Objects.toString(changeSet.getChangeLog(), ""),
            changeSet.getDescription(),
            changeSet.getComments(),
            execType == null ? "UNKNOWN" : execType.name(),
            badgeClass(execType),
            changeSet.getDateExecuted() == null ? null : DATE_EXECUTED.format(Instant.ofEpochMilli(changeSet.getDateExecuted().getTime())),
            changeSet.getTag(),
            sorted(changeSet.getContextExpression() == null ? null : changeSet.getContextExpression().getContexts()),
            sorted(changeSet.getLabels() == null ? null : changeSet.getLabels().getLabels())
        );
    }

    static String badgeClass(ChangeSet.@Nullable ExecType execType) {
        if (execType == ChangeSet.ExecType.FAILED) {
            return "badge-destructive";
        }
        if (execType == ChangeSet.ExecType.MARK_RAN || execType == ChangeSet.ExecType.SKIPPED) {
            return "badge-outline";
        }
        return "badge-secondary";
    }

    private static List<String> sorted(@Nullable Collection<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).sorted().toList();
    }
}
