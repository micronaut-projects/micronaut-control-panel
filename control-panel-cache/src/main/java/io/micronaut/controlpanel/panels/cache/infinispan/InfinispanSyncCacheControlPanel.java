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
package io.micronaut.controlpanel.panels.cache.infinispan;

import io.micronaut.cache.infinispan.InfinispanSyncCache;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Control panel for managing Infinispan caches.
 * Provides cache information and operations specific to Infinispan-based caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
public class InfinispanSyncCacheControlPanel extends AbstractCacheControlPanel<InfinispanSyncCache> {

    private final InfinispanSyncCache cache;

    /**
     * Constructor.
     *
     * @param cache the Infinispan cache instance
     * @param configuration the control panel configuration
     */
    public InfinispanSyncCacheControlPanel(InfinispanSyncCache cache, ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.cache = cache;
    }

    @Override
    protected InfinispanSyncCache getCache() {
        return cache;
    }

    @Override
    protected Map<String, Object> getCacheAsMap() {
        return cache.getNativeCache().entrySet()
            .stream()
            .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
    }

    /**
     * Returns the icon for Infinispan cache control panels.
     *
     * @return the icon name
     */
    @Override
    public String getIcon() {
        return "fa-i";
    }
}
