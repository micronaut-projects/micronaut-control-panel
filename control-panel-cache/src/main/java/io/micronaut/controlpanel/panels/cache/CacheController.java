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
package io.micronaut.controlpanel.panels.cache;


import io.micronaut.cache.SyncCache;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Optional;

/**
 * REST controller for cache management operations in the Micronaut Control Panel.
 * Provides endpoints for invalidating cache entries and entire caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Controller("/cache-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class CacheController {

    private final ControlPanelRepository repository;

    /**
     * Constructor.
     *
     * @param repository the control panel repository
     */
    public CacheController(final ControlPanelRepository repository) {
        this.repository = repository;
    }

    /**
     * Invalidates all entries in the specified cache.
     *
     * @param cacheName the name of the cache to invalidate
     * @return HTTP 204 No Content if successful, HTTP 404 Not Found if cache not found
     */
    @Delete("/{cacheName}")
    public HttpResponse<Void> invalidateAll(String cacheName) {
        try {
            var cache = findCache(cacheName);
            if (cache.isPresent()) {
                cache.get().invalidateAll();
                return HttpResponse.noContent();
            } else  {
                return HttpResponse.notFound();
            }
        } catch (ConfigurationException e) {
            return HttpResponse.notFound();
        }
    }

    /**
     * Invalidates a specific entry in the specified cache.
     *
     * @param cacheName the name of the cache
     * @param key the key of the entry to invalidate
     * @return HTTP 204 No Content if successful, HTTP 404 Not Found if cache not found
     */
    @Delete("/{cacheName}/{key}")
    public HttpResponse<Void> invalidate(String cacheName, String key) {
        try {
            var cache = findCache(cacheName);
            if (cache.isPresent()) {
                cache.get().invalidate(key);
                return HttpResponse.noContent();
            } else  {
                return HttpResponse.notFound();
            }
        } catch (ConfigurationException e) {
            return HttpResponse.notFound();
        }
    }

    private Optional<SyncCache<?>> findCache(final String cacheName) {
        return repository.findByName("cache-" + cacheName)
            .map(controlPanel -> (AbstractCacheControlPanel<?>) controlPanel)
            .map(AbstractCacheControlPanel::getCache);
    }
}
