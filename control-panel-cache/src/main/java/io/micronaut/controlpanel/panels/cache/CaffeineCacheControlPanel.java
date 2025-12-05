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

import io.micronaut.cache.caffeine.DefaultSyncCache;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;

import java.util.Map;
import java.util.Optional;

/**
 * Control panel for managing Caffeine caches.
 * Provides cache information and operations specific to Caffeine-based caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@EachBean(DefaultSyncCache.class)
public class CaffeineCacheControlPanel extends AbstractCacheControlPanel<DefaultSyncCache> {

    private final DefaultSyncCache cache;

    /**
     * Constructor.
     *
     * @param cache the Caffeine cache instance
     * @param configuration the control panel configuration
     */
    protected CaffeineCacheControlPanel(@Parameter DefaultSyncCache cache, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.cache = cache;
    }

    @Override
    protected DefaultSyncCache getCache() {
        return cache;
    }

    @Override
    protected Optional<Long> getCacheSize() {
        return Optional.of(cache.getNativeCache().estimatedSize());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getCacheAsMap() {
        return cache.getNativeCache().asMap();
    }

    /**
     * Returns the icon for Caffeine cache control panels.
     *
     * @return the icon name
     */
    @Override
    public String getIcon() {
        return "fa-mug-hot";
    }
}
