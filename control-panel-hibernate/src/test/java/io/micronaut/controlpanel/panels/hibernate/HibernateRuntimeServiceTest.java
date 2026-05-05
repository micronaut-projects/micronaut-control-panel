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

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Metamodel;
import org.hibernate.annotations.CacheLayout;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.spi.CacheImplementor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.SimpleDomainType;
import org.hibernate.metamodel.spi.MappingMetamodelImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.spi.QueryImplementor;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.CollectionStatistics;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class HibernateRuntimeServiceTest {

    @Mock
    private SessionFactoryImplementor sessionFactory;

    @Mock
    private SessionFactoryImplementor hibernateSessionFactory;

    @Mock
    private MappingMetamodelImplementor mappingMetamodel;

    @Mock
    private EntityPersister entityPersister;

    @Mock
    private SessionFactoryOptions options;

    @Mock
    private StatisticsImplementor statistics;

    @Mock
    private CacheImplementor cache;

    @Mock
    private Metamodel metamodel;

    @Mock
    private EntityDomainType<Book> entity;

    @Mock
    private SimpleDomainType<Long> idType;

    @Mock
    private Attribute<Book, String> attribute;

    @Mock
    private EntityStatistics entityStatistics;

    @Mock
    private CollectionStatistics collectionStatistics;

    @Mock
    private QueryStatistics queryStatistics;

    @Mock
    private CacheRegionStatistics cacheRegionStatistics;

    @Mock
    private SessionImplementor session;

    @Mock
    private QueryImplementor<Object> hqlQuery;

    private HibernateRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new HibernateRuntimeService("default", sessionFactory);
    }

    @Test
    void buildsBodyFromSessionFactoryMetadataAndStatistics() {
        mockSessionFactory();
        mockEntityMetadata();
        mockStatistics();

        var body = service.getBody();

        assertEquals("default", body.sessionFactory().beanName());
        assertEquals("main-session-factory", body.sessionFactory().sessionFactoryName());
        assertFalse(body.sessionFactory().closed());
        assertTrue(body.sessionFactory().statisticsEnabled());
        assertTrue(body.sessionFactory().secondLevelCacheEnabled());
        assertTrue(body.sessionFactory().queryCacheEnabled());
        assertEquals("PUBLIC", body.sessionFactory().defaultSchema());
        assertEquals("org.hibernate.dialect.H2Dialect", body.sessionFactory().properties().get("hibernate.dialect"));
        assertEquals("example.NamingStrategy", body.sessionFactory().properties().get("hibernate.naming.physical_strategy"));
        assertEquals("******", body.sessionFactory().properties().get("jakarta.persistence.jdbc.password"));
        assertEquals("******", body.sessionFactory().properties().get("hibernate.connection.token"));
        assertEquals("******", body.sessionFactory().properties().get("hibernate.connection.secret"));
        assertFalse(body.sessionFactory().properties().containsKey("micronaut.application.name"));
        assertEquals("25", body.sessionFactory().properties().get("hibernate.default_batch_fetch_size"));
        assertEquals("75", body.sessionFactory().properties().get("hibernate.jdbc.batch_size"));
        assertEquals(
            "DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION",
            body.sessionFactory().properties().get("hibernate.connection.handling_mode")
        );
        assertEquals("true", body.sessionFactory().properties().get("hibernate.cache.use_minimal_puts"));
        assertEquals("512", body.sessionFactory().properties().get("hibernate.statistics.query_max_size"));

        assertEquals(5, body.statistics().entityLoadCount());
        assertEquals(3, body.statistics().queryExecutionCount());
        assertEquals("select b from Book b", body.statistics().queryExecutionMaxTimeQueryString());

        assertEquals(1, body.entities().size());
        var entityInfo = body.entities().getFirst();
        assertEquals("Book", entityInfo.name());
        assertEquals("example.Book", entityInfo.hibernateEntityName());
        assertEquals(Book.class.getName(), entityInfo.javaType());
        assertEquals(Long.class.getName(), entityInfo.idType());
        assertEquals(1, entityInfo.attributeCount());
        assertEquals("title", entityInfo.attributes().getFirst().name());
        assertEquals(7, entityInfo.statistics().loadCount());
        assertEquals("books", entityInfo.statistics().cacheRegionName());

        assertEquals("example.Book.chapters", body.collections().getFirst().role());
        assertEquals("example.Book", body.collections().getFirst().ownerEntityName());
        assertEquals("chapters", body.collections().getFirst().attributeName());
        assertEquals(11, body.collections().getFirst().statistics().loadCount());

        assertEquals("select b from Book b", body.queries().getFirst().query());
        assertEquals(13, body.queries().getFirst().executionCount());
        assertEquals(44, body.queries().getFirst().slowTime());

        assertEquals("books", body.cacheRegions().getFirst().name());
        assertEquals(17, body.cacheRegions().getFirst().hitCount());
        assertEquals("23", body.cacheRegions().getFirst().elementCountInMemory());
        assertEquals("Not supported", body.cacheRegions().getFirst().elementCountOnDisk());
        assertEquals("Not supported", body.cacheRegions().getFirst().sizeInMemory());

        assertTrue(body.namedQueries().isEmpty());
        assertEquals(1, body.dataSources().size());
        assertEquals("default", body.dataSources().getFirst().name());
        assertEquals("jdbc:h2:mem:test", body.dataSources().getFirst().jdbcUrl());
        assertEquals("org.hibernate.dialect.H2Dialect", body.dataSources().getFirst().dialect());
    }

    @Test
    void reportsUnsupportedCacheRegionExtendedStatistics() {
        mockSessionFactory();
        mockEntityMetadata();
        mockStatistics(
            CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN,
            CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN,
            CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN
        );

        var region = service.getBody().cacheRegions().getFirst();

        assertEquals("Not supported", region.elementCountInMemory());
        assertEquals("Not supported", region.elementCountOnDisk());
        assertEquals("Not supported", region.sizeInMemory());
    }

    @Test
    void delegatesRuntimeOperationsToHibernate() {
        doReturn(statistics).when(sessionFactory).getStatistics();
        doReturn(cache).when(sessionFactory).getCache();

        service.setStatisticsEnabled(true);
        service.clearStatistics();
        service.evictAllCacheRegions();
        service.evictCacheRegion("books");
        service.evictEntityData("example.Book");
        service.evictCollectionData("example.Book.chapters");
        service.evictDefaultQueryRegion();
        service.evictQueryRegion("queries");
        service.evictQueryRegions();

        verify(statistics).setStatisticsEnabled(true);
        verify(statistics).clear();
        verify(cache).evictAllRegions();
        verify(cache).evictRegion("books");
        verify(cache).evictEntityData("example.Book");
        verify(cache).evictCollectionData("example.Book.chapters");
        verify(cache).evictDefaultQueryRegion();
        verify(cache).evictQueryRegion("queries");
        verify(cache).evictQueryRegions();
    }

    @Test
    void executesReadOnlyHqlQueriesWithPagination() {
        when(sessionFactory.openSession()).thenReturn(session);
        when(session.createQuery("from Book b", Object.class)).thenReturn(hqlQuery);
        when(hqlQuery.setReadOnly(true)).thenReturn(hqlQuery);
        when(hqlQuery.getResultCount()).thenReturn(12L);
        when(hqlQuery.setFirstResult(10)).thenReturn(hqlQuery);
        when(hqlQuery.setMaxResults(25)).thenReturn(hqlQuery);
        when(hqlQuery.getResultList()).thenReturn(List.of("Domain-Driven Hibernate", "Practical Hibernate"));

        var result = service.executeHqlQuery(" from Book b ", 10, 25, 7);

        assertEquals(7, result.get("draw"));
        assertEquals(12L, result.get("recordsTotal"));
        assertEquals(12L, result.get("recordsFiltered"));
        assertEquals(List.of("Result"), result.get("cols"));
        assertEquals(List.of(
            List.of("Domain-Driven Hibernate"),
            List.of("Practical Hibernate")
        ), result.get("data"));
        assertFalse((Boolean) result.get("hasNextPage"));
        assertFalse(result.containsKey("error"));

        verify(session).setDefaultReadOnly(true);
        verify(session).close();
    }

    @Test
    void rendersEntityHqlResultsWithDetails() {
        service = new HibernateRuntimeService("default", hibernateSessionFactory);
        var book = new Book();
        when(hibernateSessionFactory.openSession()).thenReturn(session);
        when(hibernateSessionFactory.getMappingMetamodel()).thenReturn(mappingMetamodel);
        when(mappingMetamodel.findEntityDescriptor(Book.class)).thenReturn(entityPersister);
        when(entityPersister.getEntityName()).thenReturn("example.Book");
        when(entityPersister.getIdentifierPropertyName()).thenReturn("id");
        when(entityPersister.getIdentifier(book)).thenReturn(99L);
        when(entityPersister.getPropertyNames()).thenReturn(new String[]{"title", "pages"});
        when(entityPersister.getPropertyValues(book)).thenReturn(new Object[]{"Domain-Driven Hibernate", 320});
        when(session.createQuery("from Book b", Object.class)).thenReturn(hqlQuery);
        when(hqlQuery.setReadOnly(true)).thenReturn(hqlQuery);
        when(hqlQuery.getResultCount()).thenReturn(1L);
        when(hqlQuery.setFirstResult(0)).thenReturn(hqlQuery);
        when(hqlQuery.setMaxResults(10)).thenReturn(hqlQuery);
        when(hqlQuery.getResultList()).thenReturn(List.of(book));

        var result = service.executeHqlQuery("from Book b", 0, 10, 1);
        var rows = (List<?>) result.get("data");
        var row = (List<?>) rows.getFirst();
        var cell = (Map<?, ?>) row.getFirst();
        var details = (List<?>) cell.get("details");

        assertEquals("example.Book#99", cell.get("value"));
        assertTrue(details.contains(Map.of("label", "id", "value", "99")));
        assertTrue(details.contains(Map.of("label", "title", "value", "Domain-Driven Hibernate")));
        assertTrue(details.contains(Map.of("label", "pages", "value", "320")));
    }

    @Test
    void keepsNextPageAvailableWhenHqlCountFails() {
        when(sessionFactory.openSession()).thenReturn(session);
        when(session.createQuery("from Book b", Object.class)).thenReturn(hqlQuery);
        when(hqlQuery.setReadOnly(true)).thenReturn(hqlQuery);
        when(hqlQuery.getResultCount()).thenThrow(new IllegalArgumentException("Cannot count"));
        when(hqlQuery.setFirstResult(0)).thenReturn(hqlQuery);
        when(hqlQuery.setMaxResults(11)).thenReturn(hqlQuery);
        when(hqlQuery.getResultList()).thenReturn(List.of(
            "Book 1",
            "Book 2",
            "Book 3",
            "Book 4",
            "Book 5",
            "Book 6",
            "Book 7",
            "Book 8",
            "Book 9",
            "Book 10",
            "Book 11"
        ));

        var result = service.executeHqlQuery("from Book b", 0, 10, 1);

        assertEquals(11L, result.get("recordsTotal"));
        assertEquals(11L, result.get("recordsFiltered"));
        assertTrue((Boolean) result.get("hasNextPage"));
        assertEquals(10, ((List<?>) result.get("data")).size());
        assertEquals(List.of("Book 1"), ((List<?>) result.get("data")).getFirst());
    }

    @Test
    void rejectsHqlMutations() {
        var exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.executeHqlQuery("delete from Book b", 0, 10, 1)
        );

        assertEquals("Only read-only HQL select queries are allowed", exception.getMessage());
    }

    private void mockSessionFactory() {
        doReturn(statistics).when(sessionFactory).getStatistics();
        when(sessionFactory.getSessionFactoryOptions()).thenReturn(options);
        when(sessionFactory.isClosed()).thenReturn(false);
        when(sessionFactory.getProperties()).thenReturn(Map.of(
            "hibernate.dialect", "org.hibernate.dialect.H2Dialect",
            "hibernate.connection.url", "jdbc:h2:mem:test",
            "hibernate.connection.secret", "secret-value",
            "hibernate.connection.token", "token-value",
            "hibernate.naming.physical_strategy", "example.NamingStrategy",
            "jakarta.persistence.jdbc.password", "secret",
            "micronaut.application.name", "demo"
        ));
        when(sessionFactory.getMetamodel()).thenReturn(metamodel);

        when(options.getSessionFactoryName()).thenReturn("main-session-factory");
        when(options.isSecondLevelCacheEnabled()).thenReturn(true);
        when(options.isQueryCacheEnabled()).thenReturn(true);
        when(options.getDefaultCatalog()).thenReturn("");
        when(options.getDefaultSchema()).thenReturn("PUBLIC");
        when(options.getCacheRegionPrefix()).thenReturn("");
        when(options.isStatisticsEnabled()).thenReturn(true);
        when(options.getDefaultBatchFetchSize()).thenReturn(25);
        when(options.getMaximumFetchDepth()).thenReturn(3);
        when(options.isSubselectFetchEnabled()).thenReturn(true);
        when(options.isOrderInsertsEnabled()).thenReturn(true);
        when(options.isOrderUpdatesEnabled()).thenReturn(true);
        when(options.getJdbcBatchSize()).thenReturn(75);
        when(options.getJdbcFetchSize()).thenReturn(150);
        when(options.getPhysicalConnectionHandlingMode())
            .thenReturn(PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION);
        when(options.doesConnectionProviderDisableAutoCommit()).thenReturn(true);
        when(options.isCommentsEnabled()).thenReturn(true);
        when(options.inClauseParameterPaddingEnabled()).thenReturn(true);
        when(options.getQueryStatisticsMaxSize()).thenReturn(512);
        when(options.getQueryCacheLayout()).thenReturn(CacheLayout.FULL);
        when(options.isMinimalPutsEnabled()).thenReturn(true);
        when(options.isStructuredCacheEntriesEnabled()).thenReturn(true);
        when(options.isDirectReferenceCacheEntriesEnabled()).thenReturn(false);
        when(options.isAutoEvictCollectionCache()).thenReturn(true);
        when(options.isAutoCloseSessionEnabled()).thenReturn(false);
        when(options.isFlushBeforeCompletionEnabled()).thenReturn(false);
        when(options.isAllowOutOfTransactionUpdateOperations()).thenReturn(false);
    }

    private void mockEntityMetadata() {
        when(metamodel.getEntities()).thenReturn(Set.of(entity));
        when(entity.getName()).thenReturn("Book");
        when(entity.getHibernateEntityName()).thenReturn("example.Book");
        when(entity.getJavaType()).thenReturn(Book.class);
        when(entity.hasSingleIdAttribute()).thenReturn(true);
        doReturn(idType).when(entity).getIdType();
        when(idType.getJavaType()).thenReturn(Long.class);
        when(entity.getAttributes()).thenReturn(Set.of(attribute));
        when(attribute.getName()).thenReturn("title");
        when(attribute.getPersistentAttributeType()).thenReturn(Attribute.PersistentAttributeType.BASIC);
        when(attribute.getJavaType()).thenReturn(String.class);
        when(attribute.isAssociation()).thenReturn(false);
        when(attribute.isCollection()).thenReturn(false);
    }

    private void mockStatistics() {
        mockStatistics(
            23L,
            CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN,
            CacheRegionStatistics.NO_EXTENDED_STAT_SUPPORT_RETURN
        );
    }

    private void mockStatistics(long elementCountInMemory, long elementCountOnDisk, long sizeInMemory) {
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getStart()).thenReturn(Instant.parse("2026-05-04T00:00:00Z"));
        when(statistics.getEntityLoadCount()).thenReturn(5L);
        when(statistics.getQueryExecutionCount()).thenReturn(3L);
        when(statistics.getQueryExecutionMaxTimeQueryString()).thenReturn("select b from Book b");
        when(statistics.getCollectionRoleNames()).thenReturn(new String[]{"example.Book.chapters"});
        when(statistics.getQueries()).thenReturn(new String[]{"select b from Book b"});
        when(statistics.getSlowQueries()).thenReturn(Map.of("select b from Book b", 44L));
        when(statistics.getSecondLevelCacheRegionNames()).thenReturn(new String[]{"books"});
        when(statistics.getEntityStatistics("example.Book")).thenReturn(entityStatistics);
        when(statistics.getCollectionStatistics("example.Book.chapters")).thenReturn(collectionStatistics);
        when(statistics.getQueryStatistics("select b from Book b")).thenReturn(queryStatistics);
        when(statistics.getCacheRegionStatistics("books")).thenReturn(cacheRegionStatistics);

        when(entityStatistics.getLoadCount()).thenReturn(7L);
        when(entityStatistics.getCacheRegionName()).thenReturn("books");

        when(collectionStatistics.getLoadCount()).thenReturn(11L);
        when(collectionStatistics.getCacheRegionName()).thenReturn("books");

        when(queryStatistics.getExecutionCount()).thenReturn(13L);

        when(cacheRegionStatistics.getRegionName()).thenReturn("books");
        when(cacheRegionStatistics.getHitCount()).thenReturn(17L);
        when(cacheRegionStatistics.getElementCountInMemory()).thenReturn(elementCountInMemory);
        when(cacheRegionStatistics.getElementCountOnDisk()).thenReturn(elementCountOnDisk);
        when(cacheRegionStatistics.getSizeInMemory()).thenReturn(sizeInMemory);
    }

    private static final class Book {
    }
}
