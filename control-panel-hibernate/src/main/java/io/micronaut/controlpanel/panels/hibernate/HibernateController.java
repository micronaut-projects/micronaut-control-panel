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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * REST controller for Hibernate runtime operations in the Micronaut Control Panel.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Controller("/hibernate-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class HibernateController {

    static final Argument<HibernateRuntimeService> SERVICE_ARGUMENT = Argument.of(HibernateRuntimeService.class);

    private final Map<String, HibernateRuntimeService> services;

    /**
     * Constructor.
     *
     * @param locator the bean locator
     */
    public HibernateController(BeanLocator locator) {
        this.services = locator.mapOfType(SERVICE_ARGUMENT);
    }

    /**
     * Enables or disables Hibernate statistics for a session factory.
     *
     * @param sessionFactory the session factory bean name
     * @param enabled whether statistics should be enabled
     * @return HTTP 204 if the operation was performed
     */
    @Post("/{sessionFactory}/statistics/enabled/{enabled}")
    public HttpResponse<Void> setStatisticsEnabled(String sessionFactory, boolean enabled) {
        return withService(sessionFactory, service -> service.setStatisticsEnabled(enabled));
    }

    /**
     * Executes a read-only HQL query for a session factory.
     *
     * @param sessionFactory the session factory bean name
     * @param request the HQL query request
     * @return paged query results
     */
    @Post(value = "/{sessionFactory}/hql", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> executeHql(String sessionFactory, @Body HqlQueryRequest request) {
        Optional<HibernateRuntimeService> service = Optional.ofNullable(services.get(sessionFactory));
        if (service.isEmpty()) {
            return HttpResponse.notFound();
        }
        if (request == null) {
            return HttpResponse.badRequest(queryError(null, "HQL request body is required"));
        }
        try {
            return HttpResponse.ok(service.get().executeHqlQuery(request.hql(), request.start(), request.length(), request.draw()));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(queryError(request.draw(), e.getMessage()));
        } catch (RuntimeException e) {
            return HttpResponse.serverError(queryError(request.draw(), e.getMessage()));
        }
    }

    /**
     * Clears Hibernate statistics for a session factory.
     *
     * @param sessionFactory the session factory bean name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/statistics")
    public HttpResponse<Void> clearStatistics(String sessionFactory) {
        return withService(sessionFactory, HibernateRuntimeService::clearStatistics);
    }

    /**
     * Evicts every Hibernate cache region for a session factory.
     *
     * @param sessionFactory the session factory bean name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache")
    public HttpResponse<Void> evictAllCacheRegions(String sessionFactory) {
        return withService(sessionFactory, HibernateRuntimeService::evictAllCacheRegions);
    }

    /**
     * Evicts one Hibernate cache region.
     *
     * @param sessionFactory the session factory bean name
     * @param region the cache region name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache/region")
    public HttpResponse<Void> evictCacheRegion(String sessionFactory, @QueryValue String region) {
        return withService(sessionFactory, service -> service.evictCacheRegion(region));
    }

    /**
     * Evicts cached data for one entity.
     *
     * @param sessionFactory the session factory bean name
     * @param entity the Hibernate entity name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache/entity")
    public HttpResponse<Void> evictEntityData(String sessionFactory, @QueryValue String entity) {
        return withService(sessionFactory, service -> service.evictEntityData(entity));
    }

    /**
     * Evicts cached data for one collection role.
     *
     * @param sessionFactory the session factory bean name
     * @param role the Hibernate collection role
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache/collection")
    public HttpResponse<Void> evictCollectionData(String sessionFactory, @QueryValue String role) {
        return withService(sessionFactory, service -> service.evictCollectionData(role));
    }

    /**
     * Evicts Hibernate's default query cache region.
     *
     * @param sessionFactory the session factory bean name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache/default-query-region")
    public HttpResponse<Void> evictDefaultQueryRegion(String sessionFactory) {
        return withService(sessionFactory, HibernateRuntimeService::evictDefaultQueryRegion);
    }

    /**
     * Evicts one Hibernate query cache region.
     *
     * @param sessionFactory the session factory bean name
     * @param region the query cache region name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache/query-region")
    public HttpResponse<Void> evictQueryRegion(String sessionFactory, @QueryValue String region) {
        return withService(sessionFactory, service -> service.evictQueryRegion(region));
    }

    /**
     * Evicts all Hibernate query cache regions.
     *
     * @param sessionFactory the session factory bean name
     * @return HTTP 204 if the operation was performed
     */
    @Delete("/{sessionFactory}/cache/query-regions")
    public HttpResponse<Void> evictQueryRegions(String sessionFactory) {
        return withService(sessionFactory, HibernateRuntimeService::evictQueryRegions);
    }

    private HttpResponse<Void> withService(String sessionFactory, Consumer<HibernateRuntimeService> operation) {
        Optional<HibernateRuntimeService> service = Optional.ofNullable(services.get(sessionFactory));
        if (service.isEmpty()) {
            return HttpResponse.notFound();
        }
        operation.accept(service.get());
        return HttpResponse.noContent();
    }

    private static Map<String, Object> queryError(Integer draw, String message) {
        return Map.of(
            "draw", draw == null ? 1 : draw,
            "recordsTotal", 0,
            "recordsFiltered", 0,
            "data", List.of(),
            "cols", List.of(),
            "error", message == null ? "HQL query failed" : message
        );
    }

    /**
     * HQL data table request.
     *
     * @param hql the HQL query text
     * @param start zero-based result offset
     * @param length requested page size
     * @param draw data table draw counter
     */
    @Introspected
    public record HqlQueryRequest(String hql, Integer start, Integer length, Integer draw) {
    }
}
