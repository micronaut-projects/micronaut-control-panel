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

import io.micronaut.cache.ehcache.EhcacheSyncCache;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import org.ehcache.Cache;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Control panel for managing Ehcache caches.
 * Provides cache information and operations specific to Ehcache-based caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Requires(classes = EhcacheSyncCache.class)
@EachBean(EhcacheSyncCache.class)
public class EhcacheControlPanel extends AbstractCacheControlPanel<EhcacheSyncCache> {

    private final EhcacheSyncCache cache;

    /**
     * Constructor.
     *
     * @param cache the Ehcache cache instance
     * @param configuration the control panel configuration
     */
    protected EhcacheControlPanel(@Parameter EhcacheSyncCache cache,
                                  @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.cache = cache;
    }

    @Override
    protected EhcacheSyncCache getCache() {
        return cache;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getCacheAsMap() {
        Cache<String, Object>  nativeCache = cache.getNativeCache();
        Iterable<Cache.Entry<String, Object>> iterable = nativeCache;
        return StreamSupport.stream(iterable.spliterator(), false)
            .collect(Collectors.toMap(Cache.Entry::getKey, Cache.Entry::getValue));
    }

    /**
     * Returns the icon for Ehcache control panels.
     *
     * @return the icon name
     */
    @Override
    public String getIcon() {
        return "fa-e";
    }
}
