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
package io.micronaut.controlpanel.panels.hibernate;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateBody;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateCacheRegionInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateCollectionInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateCollectionStatisticsInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateDataSourceInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateEntityAttributeInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateEntityInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateEntityStatisticsInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateNamedQueryInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateQueryInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateSessionFactoryInfo;
import io.micronaut.controlpanel.panels.hibernate.model.HibernateStatisticsInfo;
import jakarta.persistence.Tuple;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Type;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.DatabaseConnectionInfo;
import org.hibernate.engine.jdbc.env.spi.ExtractedDatabaseMetaData;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.procedure.spi.NamedCallableQueryMemento;
import org.hibernate.query.named.NamedQueryMemento;
import org.hibernate.query.Query;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.CacheableDataStatistics;
import org.hibernate.stat.CollectionStatistics;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runtime access to a Hibernate {@link SessionFactory}.
 */
@EachBean(SessionFactory.class)
@SuppressWarnings("deprecation")
final class HibernateRuntimeService {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateRuntimeService.class);
    private static final int DEFAULT_HQL_PAGE_SIZE = 10;
    private static final int MAX_HQL_PAGE_SIZE = 100;
    private static final int MAX_HQL_LENGTH = 10_000;
    private static final int MAX_HQL_ENTITY_DETAILS = 12;
    private static final String UNSUPPORTED_CACHE_METRIC = "Not supported";
    private static final List<String> DISPLAYED_PROPERTY_PREFIXES = List.of(
        "hibernate.",
        "jakarta.persistence."
    );
    private static final List<String> SENSITIVE_PROPERTY_NAME_PARTS = List.of(
        "password",
        "credential",
        "certificate",
        "key",
        "secret",
        "token"
    );

    private final String beanName;
    private final SessionFactory sessionFactory;

    HibernateRuntimeService(@Parameter String beanName, @Parameter SessionFactory sessionFactory) {
        this.beanName = beanName;
        this.sessionFactory = sessionFactory;
    }

    HibernateBody getBody() {
        var statistics = sessionFactory.getStatistics();
        return new HibernateBody(
            sessionFactoryInfo(statistics),
            statisticsInfo(statistics),
            entityInfos(statistics),
            collectionInfos(statistics),
            queryInfos(statistics),
            cacheRegionInfos(statistics),
            namedQueryInfos(),
            dataSourceInfos()
        );
    }

    Map<String, Object> executeHqlQuery(String hql, Integer start, Integer length, Integer draw) {
        var queryText = hql == null ? "" : hql.trim();
        validateHqlQuery(queryText);
        var pagination = hqlPagination(start, length);

        try (var session = sessionFactory.openSession()) {
            session.setDefaultReadOnly(true);
            var query = session.createQuery(queryText, Object.class)
                .setReadOnly(true);
            var total = resultCount(query);
            var fetchedRows = query
                .setFirstResult(pagination.first())
                .setMaxResults(fetchSize(total, pagination.pageSize()))
                .getResultList();
            return hqlResult(draw, pagination, total, fetchedRows);
        }
    }

    void setStatisticsEnabled(boolean enabled) {
        sessionFactory.getStatistics().setStatisticsEnabled(enabled);
    }

    void clearStatistics() {
        sessionFactory.getStatistics().clear();
    }

    void evictAllCacheRegions() {
        sessionFactory.getCache().evictAllRegions();
    }

    void evictCacheRegion(String region) {
        sessionFactory.getCache().evictRegion(region);
    }

    void evictEntityData(String entityName) {
        sessionFactory.getCache().evictEntityData(entityName);
    }

    void evictCollectionData(String role) {
        sessionFactory.getCache().evictCollectionData(role);
    }

    void evictDefaultQueryRegion() {
        sessionFactory.getCache().evictDefaultQueryRegion();
    }

    void evictQueryRegion(String region) {
        sessionFactory.getCache().evictQueryRegion(region);
    }

    void evictQueryRegions() {
        sessionFactory.getCache().evictQueryRegions();
    }

    private HibernateSessionFactoryInfo sessionFactoryInfo(Statistics statistics) {
        var options = sessionFactoryOptions();
        return new HibernateSessionFactoryInfo(
            beanName,
            options.map(SessionFactoryOptions::getSessionFactoryName).orElse(""),
            sessionFactory.isClosed(),
            statistics.isStatisticsEnabled(),
            options.map(SessionFactoryOptions::isSecondLevelCacheEnabled).orElse(false),
            options.map(SessionFactoryOptions::isQueryCacheEnabled).orElse(false),
            options.map(SessionFactoryOptions::getDefaultCatalog).orElse(""),
            options.map(SessionFactoryOptions::getDefaultSchema).orElse(""),
            options.map(SessionFactoryOptions::getCacheRegionPrefix).orElse(""),
            sessionFactoryProperties(options)
        );
    }

    private Optional<SessionFactoryOptions> sessionFactoryOptions() {
        if (!(sessionFactory instanceof SessionFactoryImplementor sessionFactoryImplementor)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(sessionFactoryImplementor.getSessionFactoryOptions());
        } catch (RuntimeException e) {
            LOG.debug("Cannot read Hibernate SessionFactoryOptions for bean '{}': {}", beanName, e.getMessage());
            return Optional.empty();
        }
    }

    private HibernateStatisticsInfo statisticsInfo(Statistics statistics) {
        return new HibernateStatisticsInfo(
            statistics.isStatisticsEnabled(),
            valueOrEmpty(statistics.getStart()),
            statistics.getSessionOpenCount(),
            statistics.getSessionCloseCount(),
            statistics.getTransactionCount(),
            statistics.getSuccessfulTransactionCount(),
            statistics.getConnectCount(),
            statistics.getFlushCount(),
            statistics.getPrepareStatementCount(),
            statistics.getCloseStatementCount(),
            statistics.getEntityLoadCount(),
            statistics.getEntityFetchCount(),
            statistics.getEntityInsertCount(),
            statistics.getEntityUpdateCount(),
            statistics.getEntityDeleteCount(),
            statistics.getCollectionLoadCount(),
            statistics.getCollectionFetchCount(),
            statistics.getCollectionUpdateCount(),
            statistics.getCollectionRemoveCount(),
            statistics.getCollectionRecreateCount(),
            statistics.getQueryExecutionCount(),
            statistics.getQueryExecutionMaxTime(),
            valueOrEmpty(statistics.getQueryExecutionMaxTimeQueryString()),
            statistics.getQueryCacheHitCount(),
            statistics.getQueryCacheMissCount(),
            statistics.getQueryCachePutCount(),
            statistics.getSecondLevelCacheHitCount(),
            statistics.getSecondLevelCacheMissCount(),
            statistics.getSecondLevelCachePutCount(),
            statistics.getNaturalIdCacheHitCount(),
            statistics.getNaturalIdCacheMissCount(),
            statistics.getNaturalIdCachePutCount(),
            statistics.getQueryPlanCacheHitCount(),
            statistics.getQueryPlanCacheMissCount(),
            statistics.getOptimisticFailureCount()
        );
    }

    private List<HibernateEntityInfo> entityInfos(Statistics statistics) {
        try {
            return sessionFactory.getMetamodel().getEntities()
                .stream()
                .map(entity -> entityInfo(entity, statistics))
                .sorted(Comparator.comparing(HibernateEntityInfo::name))
                .toList();
        } catch (RuntimeException e) {
            LOG.warn("Exception while reading Hibernate entity metadata for bean '{}': {}", beanName, e.getMessage());
            return List.of();
        }
    }

    private HibernateEntityInfo entityInfo(EntityType<?> entity, Statistics statistics) {
        var entityName = hibernateEntityName(entity);
        return new HibernateEntityInfo(
            entity.getName(),
            entityName,
            entity.getJavaType().getName(),
            idType(entity),
            entity.getAttributes().size(),
            attributeInfos(entity.getAttributes()),
            entityStatistics(entityName, statistics)
        );
    }

    private static String hibernateEntityName(EntityType<?> entity) {
        if (entity instanceof EntityDomainType<?> domainType) {
            return domainType.getHibernateEntityName();
        }
        return entity.getName();
    }

    private static String idType(EntityType<?> entity) {
        try {
            if (entity.hasSingleIdAttribute()) {
                Type<?> idType = entity.getIdType();
                if (idType != null && idType.getJavaType() != null) {
                    return idType.getJavaType().getName();
                }
            }
            return entity.getIdClassAttributes()
                .stream()
                .map(Attribute::getName)
                .sorted()
                .collect(Collectors.joining(", "));
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOG.trace("Cannot resolve Hibernate entity identifier type", e);
            return "";
        }
    }

    private static List<HibernateEntityAttributeInfo> attributeInfos(Set<? extends Attribute<?, ?>> attributes) {
        return attributes.stream()
            .map(attribute -> new HibernateEntityAttributeInfo(
                attribute.getName(),
                attribute.getPersistentAttributeType().name(),
                attribute.getJavaType().getName(),
                attribute.isAssociation(),
                attribute.isCollection()
            ))
            .sorted(Comparator.comparing(HibernateEntityAttributeInfo::name))
            .toList();
    }

    private static HibernateEntityStatisticsInfo entityStatistics(String entityName, Statistics statistics) {
        EntityStatistics entityStatistics = statistics.getEntityStatistics(entityName);
        return new HibernateEntityStatisticsInfo(
            entityStatistics.getLoadCount(),
            entityStatistics.getFetchCount(),
            entityStatistics.getInsertCount(),
            entityStatistics.getUpdateCount(),
            entityStatistics.getDeleteCount(),
            entityStatistics.getOptimisticFailureCount(),
            valueOrEmpty(entityStatistics.getCacheRegionName()),
            cacheCount(entityStatistics.getCacheHitCount()),
            cacheCount(entityStatistics.getCacheMissCount()),
            cacheCount(entityStatistics.getCachePutCount())
        );
    }

    private static List<HibernateCollectionInfo> collectionInfos(Statistics statistics) {
        return Arrays.stream(statistics.getCollectionRoleNames())
            .sorted()
            .map(role -> collectionInfo(role, statistics.getCollectionStatistics(role)))
            .toList();
    }

    private static HibernateCollectionInfo collectionInfo(String role, CollectionStatistics statistics) {
        var split = role.lastIndexOf('.');
        var ownerEntityName = split < 0 ? "" : role.substring(0, split);
        var attributeName = split < 0 ? "" : role.substring(split + 1);
        return new HibernateCollectionInfo(
            role,
            ownerEntityName,
            attributeName,
            new HibernateCollectionStatisticsInfo(
                statistics.getLoadCount(),
                statistics.getFetchCount(),
                statistics.getUpdateCount(),
                statistics.getRemoveCount(),
                statistics.getRecreateCount(),
                valueOrEmpty(statistics.getCacheRegionName()),
                cacheCount(statistics.getCacheHitCount()),
                cacheCount(statistics.getCacheMissCount()),
                cacheCount(statistics.getCachePutCount())
            )
        );
    }

    private static List<HibernateQueryInfo> queryInfos(Statistics statistics) {
        var slowQueries = statistics.getSlowQueries();
        return Arrays.stream(statistics.getQueries())
            .sorted()
            .map(query -> queryInfo(query, statistics.getQueryStatistics(query), slowQueries))
            .toList();
    }

    private List<HibernateNamedQueryInfo> namedQueryInfos() {
        if (!(sessionFactory instanceof SessionFactoryImplementor sessionFactoryImplementor)) {
            return List.of();
        }
        try {
            var namedObjectRepository = sessionFactoryImplementor.getQueryEngine().getNamedObjectRepository();
            var result = new ArrayList<HibernateNamedQueryInfo>();
            namedObjectRepository.visitSqmQueryMementos(query -> result.add(namedQueryInfo(query, "HQL", query.getHqlString())));
            namedObjectRepository.visitNativeQueryMementos(query -> result.add(namedQueryInfo(query, "Native SQL", query.getSqlString())));
            namedObjectRepository.visitCallableQueryMementos(query -> result.add(namedCallableQueryInfo(query)));
            return result.stream()
                .sorted(Comparator.comparing(HibernateNamedQueryInfo::type).thenComparing(HibernateNamedQueryInfo::name))
                .toList();
        } catch (RuntimeException e) {
            LOG.warn("Exception while reading Hibernate named queries for bean '{}': {}", beanName, e.getMessage());
            return List.of();
        }
    }

    private static HibernateNamedQueryInfo namedQueryInfo(NamedQueryMemento<?> query, String type, String queryText) {
        return new HibernateNamedQueryInfo(
            query.getRegistrationName(),
            type,
            valueOrEmpty(queryText),
            valueOrEmpty(query.getCacheable()),
            valueOrEmpty(query.getCacheRegion()),
            valueOrEmpty(query.getReadOnly()),
            valueOrEmpty(query.getTimeout()),
            valueOrEmpty(query.getFetchSize()),
            valueOrEmpty(query.getComment())
        );
    }

    private static HibernateNamedQueryInfo namedCallableQueryInfo(NamedCallableQueryMemento query) {
        return namedQueryInfo(query, "Callable", query.getCallableName());
    }

    private static HibernateQueryInfo queryInfo(String query, QueryStatistics statistics, Map<String, Long> slowQueries) {
        return new HibernateQueryInfo(
            query,
            statistics.getExecutionCount(),
            statistics.getExecutionRowCount(),
            statistics.getExecutionAvgTime(),
            statistics.getExecutionMaxTime(),
            statistics.getExecutionMinTime(),
            statistics.getExecutionTotalTime(),
            statistics.getCacheHitCount(),
            statistics.getCacheMissCount(),
            statistics.getCachePutCount(),
            statistics.getPlanCacheHitCount(),
            statistics.getPlanCacheMissCount(),
            slowQueries.getOrDefault(query, 0L)
        );
    }

    private static List<HibernateCacheRegionInfo> cacheRegionInfos(Statistics statistics) {
        return Arrays.stream(statistics.getSecondLevelCacheRegionNames())
            .sorted()
            .map(regionName -> cacheRegionInfo(statistics.getCacheRegionStatistics(regionName)))
            .toList();
    }

    private List<HibernateDataSourceInfo> dataSourceInfos() {
        try {
            var properties = dataSourceProperties(sessionFactory.getProperties());
            var jdbcServices = jdbcServices();
            var connectionInfo = jdbcServices.flatMap(this::databaseConnectionInfo);
            return List.of(dataSourceInfo(properties, jdbcServices, connectionInfo));
        } catch (RuntimeException e) {
            LOG.debug("Cannot read Hibernate datasource metadata for bean '{}'", beanName, e);
            return List.of();
        }
    }

    private HibernateDataSourceInfo dataSourceInfo(Map<String, String> properties,
                                                   Optional<JdbcServices> jdbcServices,
                                                   Optional<DatabaseConnectionInfo> connectionInfo) {
        return new HibernateDataSourceInfo(
            beanName,
            dataSourceJdbcUrl(properties, connectionInfo),
            dataSourceJdbcDriver(properties, connectionInfo),
            dataSourceDialect(properties, jdbcServices),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getDialectVersion),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getCatalog),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getSchema),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getAutoCommitMode),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getIsolationLevel),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getPoolMinSize),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getPoolMaxSize),
            connectionValue(connectionInfo, DatabaseConnectionInfo::getJdbcFetchSize),
            databaseSupports(jdbcServices, ExtractedDatabaseMetaData::supportsSchemas),
            databaseSupports(jdbcServices, ExtractedDatabaseMetaData::supportsCatalogs),
            databaseSupports(jdbcServices, ExtractedDatabaseMetaData::supportsNamedParameters),
            databaseSupports(jdbcServices, ExtractedDatabaseMetaData::supportsScrollableResults),
            databaseSupports(jdbcServices, ExtractedDatabaseMetaData::supportsBatchUpdates),
            databaseSupports(jdbcServices, ExtractedDatabaseMetaData::supportsGetGeneratedKeys),
            properties
        );
    }

    private static String dataSourceJdbcUrl(Map<String, String> properties, Optional<DatabaseConnectionInfo> connectionInfo) {
        return connectionInfo
            .map(DatabaseConnectionInfo::getJdbcUrl)
            .map(HibernateRuntimeService::valueOrEmpty)
            .map(HibernateRuntimeService::safeUrl)
            .orElseGet(() -> property(properties, "hibernate.connection.url", "jakarta.persistence.jdbc.url"));
    }

    private static String dataSourceJdbcDriver(Map<String, String> properties, Optional<DatabaseConnectionInfo> connectionInfo) {
        return connectionInfo
            .map(DatabaseConnectionInfo::getJdbcDriver)
            .map(HibernateRuntimeService::valueOrEmpty)
            .orElseGet(() -> property(properties, "hibernate.connection.driver_class"));
    }

    private static String dataSourceDialect(Map<String, String> properties, Optional<JdbcServices> jdbcServices) {
        return jdbcServices
            .map(services -> services.getDialect().getClass().getName())
            .orElseGet(() -> property(properties, "hibernate.dialect"));
    }

    private static String connectionValue(Optional<DatabaseConnectionInfo> connectionInfo, Function<DatabaseConnectionInfo, Object> value) {
        return connectionInfo
            .map(value)
            .map(HibernateRuntimeService::valueOrEmpty)
            .orElse("");
    }

    private static boolean databaseSupports(Optional<JdbcServices> jdbcServices, Function<ExtractedDatabaseMetaData, Boolean> support) {
        return jdbcServices
            .map(JdbcServices::getExtractedMetaDataSupport)
            .map(support)
            .orElse(false);
    }

    private Optional<JdbcServices> jdbcServices() {
        if (sessionFactory instanceof SessionFactoryImplementor sessionFactoryImplementor) {
            return Optional.ofNullable(sessionFactoryImplementor.getJdbcServices());
        }
        return Optional.empty();
    }

    private Optional<DatabaseConnectionInfo> databaseConnectionInfo(JdbcServices jdbcServices) {
        if (!(sessionFactory instanceof SessionFactoryImplementor sessionFactoryImplementor)) {
            return Optional.empty();
        }
        try {
            var provider = sessionFactoryImplementor.getServiceRegistry().getService(ConnectionProvider.class);
            return Optional.ofNullable(provider.getDatabaseConnectionInfo(jdbcServices.getDialect(), jdbcServices.getExtractedMetaDataSupport()));
        } catch (RuntimeException e) {
            LOG.debug("Cannot read Hibernate connection info for bean '{}'", beanName, e);
            return Optional.empty();
        }
    }

    private static HibernateCacheRegionInfo cacheRegionInfo(CacheRegionStatistics statistics) {
        return new HibernateCacheRegionInfo(
            statistics.getRegionName(),
            statistics.getHitCount(),
            statistics.getMissCount(),
            statistics.getPutCount(),
            extendedMetric(statistics.getElementCountInMemory()),
            extendedMetric(statistics.getElementCountOnDisk()),
            extendedMetric(statistics.getSizeInMemory())
        );
    }

    private Map<String, String> sessionFactoryProperties(Optional<SessionFactoryOptions> options) {
        var result = displayedProperties(sessionFactory.getProperties());
        options.ifPresent(sessionFactoryOptions -> addSessionFactoryOptions(result, sessionFactoryOptions));
        return result;
    }

    private static void addSessionFactoryOptions(Map<String, String> properties, SessionFactoryOptions options) {
        putOption(properties, "hibernate.generate_statistics", options.isStatisticsEnabled());
        putOption(properties, "hibernate.default_batch_fetch_size", options.getDefaultBatchFetchSize());
        putOption(properties, "hibernate.max_fetch_depth", options.getMaximumFetchDepth());
        putOption(properties, "hibernate.use_subselect_fetch", options.isSubselectFetchEnabled());
        putOption(properties, "hibernate.order_inserts", options.isOrderInsertsEnabled());
        putOption(properties, "hibernate.order_updates", options.isOrderUpdatesEnabled());
        putOption(properties, "hibernate.jdbc.batch_size", options.getJdbcBatchSize());
        putOption(properties, "hibernate.jdbc.fetch_size", options.getJdbcFetchSize());
        if (options.getJdbcTimeZone() != null) {
            putOption(properties, "hibernate.jdbc.time_zone", options.getJdbcTimeZone().getID());
        }
        putOption(properties, "hibernate.connection.handling_mode", options.getPhysicalConnectionHandlingMode());
        putOption(properties, "hibernate.connection.provider_disables_autocommit", options.doesConnectionProviderDisableAutoCommit());
        putOption(properties, "hibernate.use_sql_comments", options.isCommentsEnabled());
        putOption(properties, "hibernate.query.in_clause_parameter_padding", options.inClauseParameterPaddingEnabled());
        putOption(properties, "hibernate.statistics.query_max_size", options.getQueryStatisticsMaxSize());
        putOption(properties, "hibernate.cache.query_cache_layout", options.getQueryCacheLayout());
        putOption(properties, "hibernate.cache.use_minimal_puts", options.isMinimalPutsEnabled());
        putOption(properties, "hibernate.cache.use_structured_entries", options.isStructuredCacheEntriesEnabled());
        putOption(properties, "hibernate.cache.use_reference_entries", options.isDirectReferenceCacheEntriesEnabled());
        putOption(properties, "hibernate.cache.auto_evict_collection_cache", options.isAutoEvictCollectionCache());
        putOption(properties, "hibernate.transaction.auto_close_session", options.isAutoCloseSessionEnabled());
        putOption(properties, "hibernate.transaction.flush_before_completion", options.isFlushBeforeCompletionEnabled());
        putOption(properties, "hibernate.allow_update_outside_transaction", options.isAllowOutOfTransactionUpdateOperations());
    }

    private static Map<String, String> displayedProperties(Map<String, Object> properties) {
        var result = new LinkedHashMap<String, String>();
        if (properties == null || properties.isEmpty()) {
            return result;
        }
        properties.entrySet().stream()
            .filter(entry -> shouldDisplayProperty(entry.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> result.put(entry.getKey(), safePropertyValue(entry.getKey(), entry.getValue())));
        return result;
    }

    private static boolean shouldDisplayProperty(String key) {
        return DISPLAYED_PROPERTY_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private static Map<String, String> dataSourceProperties(Map<String, Object> properties) {
        var displayed = displayedProperties(properties);
        var result = new LinkedHashMap<String, String>();
        displayed.forEach((key, value) -> {
            if (isDataSourceProperty(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static boolean isDataSourceProperty(String key) {
        return key.contains("connection")
            || key.contains(".jdbc.")
            || key.endsWith("jtaDataSource")
            || key.endsWith("nonJtaDataSource")
            || key.equals("hibernate.dialect")
            || key.equals("hibernate.hbm2ddl.auto")
            || key.equals("jakarta.persistence.schema-generation.database.action");
    }

    private static String property(Map<String, String> properties, String... keys) {
        for (String key : keys) {
            var value = properties.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static void putOption(Map<String, String> properties, String key, Object value) {
        if (value == null) {
            return;
        }
        var stringValue = valueOrEmpty(value);
        if (!stringValue.isBlank()) {
            properties.putIfAbsent(key, safePropertyValue(key, stringValue));
        }
    }

    private static String safePropertyValue(String key, Object value) {
        var normalizedKey = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_PROPERTY_NAME_PARTS.stream().anyMatch(normalizedKey::contains)) {
            return "******";
        }
        var stringValue = valueOrEmpty(value);
        if (key.endsWith(".url") || key.endsWith("connection.url")) {
            return safeUrl(stringValue);
        }
        return stringValue;
    }

    private static String safeUrl(String url) {
        if (url.isBlank()) {
            return "";
        }
        int at = url.indexOf('@');
        int colonSlash = url.indexOf("://");
        if (at > -1 && colonSlash > -1 && at > colonSlash) {
            return url.substring(0, colonSlash + 3) + "***@" + url.substring(at + 1);
        }
        return url;
    }

    private static long cacheCount(long value) {
        return value == CacheableDataStatistics.NOT_CACHED_COUNT ? 0 : value;
    }

    private static String extendedMetric(long value) {
        return value == CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN ? UNSUPPORTED_CACHE_METRIC : Long.toString(value);
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void validateHqlQuery(String queryText) {
        if (queryText.isBlank()) {
            throw new IllegalArgumentException("HQL must not be empty");
        }
        if (queryText.length() > MAX_HQL_LENGTH) {
            throw new IllegalArgumentException("HQL is too long");
        }
        var normalized = queryText.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select ") && !normalized.startsWith("from ") && !normalized.startsWith("with ")) {
            throw new IllegalArgumentException("Only read-only HQL select queries are allowed");
        }
    }

    private static long resultCount(Query<?> query) {
        try {
            return query.getResultCount();
        } catch (RuntimeException e) {
            LOG.trace("Cannot calculate HQL result count", e);
            return -1;
        }
    }

    private Map<String, Object> hqlResult(Integer draw, HqlPagination pagination, long total, List<?> fetchedRows) {
        var hasNextPage = hasNextPage(total, pagination, fetchedRows);
        var rows = visibleRows(pagination.pageSize(), hasNextPage, fetchedRows);
        var formattedRows = rows.stream()
            .map(this::hqlRow)
            .toList();
        var records = recordCount(total, pagination.first(), rows.size(), hasNextPage);
        var result = new LinkedHashMap<String, Object>();
        result.put("draw", draw == null ? 1 : draw);
        result.put("recordsTotal", records);
        result.put("recordsFiltered", records);
        result.put("data", formattedRows);
        result.put("cols", hqlColumns(rows));
        result.put("hasNextPage", hasNextPage);
        return result;
    }

    private static HqlPagination hqlPagination(Integer start, Integer length) {
        var first = Math.clamp(start == null ? 0L : start.longValue(), 0, Integer.MAX_VALUE);
        var pageSize = Math.clamp(length == null ? DEFAULT_HQL_PAGE_SIZE : length.longValue(), 1, MAX_HQL_PAGE_SIZE);
        return new HqlPagination(first, pageSize);
    }

    private static int fetchSize(long total, int pageSize) {
        if (total < 0) {
            return pageSize + 1;
        }
        return pageSize;
    }

    private static boolean hasNextPage(long total, HqlPagination pagination, List<?> fetchedRows) {
        if (total < 0) {
            return fetchedRows.size() > pagination.pageSize();
        }
        return pagination.first() + fetchedRows.size() < total;
    }

    private static List<?> visibleRows(int pageSize, boolean hasNextPage, List<?> fetchedRows) {
        if (hasNextPage && fetchedRows.size() > pageSize) {
            return fetchedRows.subList(0, pageSize);
        }
        return fetchedRows;
    }

    private static long recordCount(long total, int first, int rowCount, boolean hasNextPage) {
        if (total >= 0) {
            return total;
        }
        var recordCount = first + rowCount;
        if (hasNextPage) {
            return recordCount + 1L;
        }
        return recordCount;
    }

    private static List<String> hqlColumns(List<?> rows) {
        if (rows.isEmpty()) {
            return List.of("Result");
        }
        var row = rows.getFirst();
        if (row instanceof Object[] values) {
            return numberedColumns(values.length);
        }
        if (row instanceof Tuple tuple) {
            var aliases = tuple.getElements().stream()
                .map(element -> valueOrEmpty(element.getAlias()))
                .toList();
            if (aliases.stream().anyMatch(alias -> !alias.isBlank())) {
                return aliases;
            }
            return numberedColumns(tuple.getElements().size());
        }
        return List.of("Result");
    }

    private static List<String> numberedColumns(int count) {
        var columns = new ArrayList<String>(count);
        for (int i = 1; i <= count; i++) {
            columns.add("Column " + i);
        }
        return columns;
    }

    private List<Object> hqlRow(Object row) {
        if (row instanceof Object[] values) {
            return Arrays.stream(values)
                .map(this::hqlCell)
                .toList();
        }
        if (row instanceof Tuple tuple) {
            return tuple.getElements().stream()
                .map(tupleElement -> hqlCell(tuple.get(tupleElement)))
                .toList();
        }
        return List.of(hqlCell(row));
    }

    private Object hqlCell(Object value) {
        if (value == null || isSimpleValue(value)) {
            return displayValue(value);
        }
        var entityPersister = entityPersister(value).orElse(null);
        if (entityPersister == null) {
            return displayValue(value);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("value", entityReference(value, entityPersister));
        result.put("details", entityDetails(value, entityPersister));
        return result;
    }

    private Optional<EntityPersister> entityPersister(Object value) {
        if (!(sessionFactory instanceof SessionFactoryImplementor sessionFactoryImplementor)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(sessionFactoryImplementor.getMappingMetamodel().findEntityDescriptor(Hibernate.getClassLazy(value)));
        } catch (RuntimeException e) {
            LOG.trace("Cannot resolve Hibernate entity persister for value of type '{}'", value.getClass().getName(), e);
            return Optional.empty();
        }
    }

    private String entityReference(Object value, EntityPersister entityPersister) {
        var identifier = entityIdentifier(value, entityPersister);
        var entityName = valueOrEmpty(entityPersister.getEntityName());
        var identifierText = displayValue(identifier);
        return identifierText.isBlank() ? entityName : entityName + "#" + identifierText;
    }

    private Object entityIdentifier(Object value, EntityPersister entityPersister) {
        try {
            return entityPersister.getIdentifier(value);
        } catch (RuntimeException e) {
            LOG.trace("Cannot resolve Hibernate entity identifier for value of type '{}'", value.getClass().getName(), e);
            return null;
        }
    }

    private List<Map<String, String>> entityDetails(Object value, EntityPersister entityPersister) {
        var details = new ArrayList<Map<String, String>>();
        details.add(hqlCellDetail("Type", valueOrEmpty(entityPersister.getEntityName())));
        var identifierName = valueOrEmpty(entityPersister.getIdentifierPropertyName());
        details.add(hqlCellDetail(identifierName.isBlank() ? "Identifier" : identifierName, displayValue(entityIdentifier(value, entityPersister))));
        try {
            var propertyNames = entityPersister.getPropertyNames();
            var propertyValues = entityPersister.getPropertyValues(value);
            for (int i = 0; i < propertyNames.length && i < propertyValues.length && details.size() < MAX_HQL_ENTITY_DETAILS; i++) {
                details.add(hqlCellDetail(propertyNames[i], detailValue(propertyValues[i])));
            }
        } catch (RuntimeException e) {
            LOG.trace("Cannot resolve Hibernate entity property values for value of type '{}'", value.getClass().getName(), e);
        }
        return details;
    }

    private static Map<String, String> hqlCellDetail(String label, String value) {
        var detail = new LinkedHashMap<String, String>();
        detail.put("label", label);
        detail.put("value", value);
        return detail;
    }

    private String detailValue(Object value) {
        if (value == null || isSimpleValue(value)) {
            return displayValue(value);
        }
        if (!Hibernate.isInitialized(value)) {
            return "Not initialized";
        }
        if (value instanceof Collection<?> collection) {
            return collection.size() + " item" + (collection.size() == 1 ? "" : "s");
        }
        var entityPersister = entityPersister(value).orElse(null);
        if (entityPersister != null) {
            return entityReference(value, entityPersister);
        }
        return value.getClass().getName();
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof CharSequence
            || value instanceof Number
            || value instanceof Boolean
            || value instanceof Enum<?>
            || value instanceof TemporalAccessor
            || value instanceof Date
            || value instanceof UUID;
    }

    private static String displayValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private record HqlPagination(int first, int pageSize) {
    }
}
