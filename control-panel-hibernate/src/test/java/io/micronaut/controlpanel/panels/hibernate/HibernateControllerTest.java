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

import io.micronaut.context.BeanLocator;
import io.micronaut.http.HttpStatus;
import org.hibernate.Cache;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HibernateControllerTest {

    @Mock
    private BeanLocator beanLocator;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private Statistics statistics;

    @Mock
    private Cache cache;

    @Test
    void delegatesStatisticsOperationsToNamedSessionFactoryService() {
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        var controller = controller(Map.of("default", new HibernateRuntimeService("default", sessionFactory)));

        assertEquals(HttpStatus.NO_CONTENT, controller.setStatisticsEnabled("default", true).getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.clearStatistics("default").getStatus());

        verify(statistics).setStatisticsEnabled(true);
        verify(statistics).clear();
    }

    @Test
    void delegatesCacheOperationsToNamedSessionFactoryService() {
        when(sessionFactory.getCache()).thenReturn(cache);
        var controller = controller(Map.of("default", new HibernateRuntimeService("default", sessionFactory)));

        assertEquals(HttpStatus.NO_CONTENT, controller.evictAllCacheRegions("default").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.evictCacheRegion("default", "books").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.evictEntityData("default", "example.Book").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.evictCollectionData("default", "example.Book.chapters").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.evictDefaultQueryRegion("default").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.evictQueryRegion("default", "queries").getStatus());
        assertEquals(HttpStatus.NO_CONTENT, controller.evictQueryRegions("default").getStatus());

        verify(cache).evictAllRegions();
        verify(cache).evictRegion("books");
        verify(cache).evictEntityData("example.Book");
        verify(cache).evictCollectionData("example.Book.chapters");
        verify(cache).evictDefaultQueryRegion();
        verify(cache).evictQueryRegion("queries");
        verify(cache).evictQueryRegions();
    }

    @Test
    void returnsNotFoundForUnknownSessionFactory() {
        var controller = controller(Map.of());

        assertEquals(HttpStatus.NOT_FOUND, controller.clearStatistics("missing").getStatus());
    }

    @Test
    void returnsBadRequestForMissingHqlRequestBody() {
        var controller = controller(Map.of("default", new HibernateRuntimeService("default", sessionFactory)));

        var response = controller.executeHql("default", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertEquals("HQL request body is required", response.body().get("error"));
    }

    private HibernateController controller(Map<String, HibernateRuntimeService> services) {
        when(beanLocator.mapOfType(HibernateController.SERVICE_ARGUMENT)).thenReturn(services);
        return new HibernateController(beanLocator);
    }
}
