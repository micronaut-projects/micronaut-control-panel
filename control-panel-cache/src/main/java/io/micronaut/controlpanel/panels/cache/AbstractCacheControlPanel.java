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

import io.micronaut.cache.Cache;
import io.micronaut.cache.CacheInfo;
import io.micronaut.cache.SyncCache;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.TypeHint;
import jakarta.inject.Named;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Base class for control panels for managing caches.
 * This class provides common functionality for displaying cache information
 * and operations in the Micronaut Control Panel.
 *
 * @param <C> the cache type
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
public abstract class AbstractCacheControlPanel<C extends SyncCache<?>> extends AbstractEachBeanControlPanel<AbstractCacheControlPanel.Body> {

    /**
     * The name of the cache control panel category.
     */
    public static final String NAME = "cache";

    /**
     * The property name to enable/disable cache control panels.
     */
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    /**
     * Constructor.
     *
     * @param controlPanelName the control panel name
     * @param configuration the control panel configuration
     */
    protected AbstractCacheControlPanel(String controlPanelName, @Named(NAME) ControlPanelConfiguration configuration) {
        super(controlPanelName, configuration);
    }

    protected abstract C getCache();

    @Override
    protected String getBeanName() {
        return getCache().getName();
    }

    @Override
    public Body getBody() {
        return new Body(getCache(), Mono.from(getCache().getCacheInfo()).block(), getCacheAsMap());
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    /**
     * Returns the badge displaying the cache size, or the default badge if size cannot be determined.
     *
     * @return the badge text
     */
    @Override
    public String getBadge() {
        return getCacheSize()
            .map(String::valueOf)
            .orElseGet(super::getBadge);
    }

    /**
     * Attempts to determine the size of the cache.
     * Supports Map-based caches and Iterable-based caches.
     * Subclasses can override this method to provide more accurate size calculations
     * for specific cache implementations.
     *
     * @return an Optional containing the cache size, or empty if it cannot be determined
     */
    protected Optional<Long> getCacheSize() {
        if (getCache().getNativeCache() instanceof Map<?, ?> mapBasedCache) {
            return Optional.of((long) mapBasedCache.size());
        } else if (getCache().getNativeCache() instanceof Iterable<?> iterableCache) {
            return Optional.of(StreamSupport.stream(iterableCache.spliterator(), false).count());
        }
        return Optional.empty();
    }

    protected abstract Map<String, Object> getCacheAsMap();

    /**
     * Returns the category for cache control panels.
     *
     * @return the cache category
     */
    @Override
    public Category getCategory() {
        return new Category(NAME, "Cache", "fa-memory");
    }

    /**
     * Returns the icon for cache control panels.
     *
     * @return the icon name
     */
    @Override
    public String getIcon() {
        return "fa-memory";
    }

    /**
     * Record containing the data to display in the cache control panel.
     *
     * @param cache the cache instance
     * @param cacheInfo the cache information
     * @param cacheAsMap the cache contents as a map
     */
    @ReflectiveAccess
    @TypeHint(value = { Cache.class, CacheInfo.class }, accessType = TypeHint.AccessType.ALL_PUBLIC)
    public record Body(Cache<?> cache, CacheInfo cacheInfo, Map<String, Object> cacheAsMap) { }
}
