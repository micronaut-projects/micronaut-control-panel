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

import io.micronaut.cache.infinispan.InfinispanCacheInfo;
import io.micronaut.cache.infinispan.InfinispanCacheManager;
import io.micronaut.cache.infinispan.InfinispanSyncCache;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelLoader;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel.NAME;

/**
 * Loader for Infinispan cache control panels.
 * Dynamically loads control panels for all available Infinispan caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Context
@Requires(beans = ControlPanelConfiguration.class)
@Requires(property = "infinispan.enabled", notEquals = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
@TypeHint(value = { InfinispanCacheInfo.class }, accessType = TypeHint.AccessType.ALL_PUBLIC)
public class InfinispanControlPanelLoader implements ControlPanelLoader {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanControlPanelLoader.class);

    private final ControlPanelConfiguration configuration;

    private final InfinispanCacheManager micronautCacheManager;
    private final RemoteCacheManager infinispanCacheManager;

    /**
     * Constructor.
     *
     * @param configuration the control panel configuration
     * @param micronautCacheManager the Micronaut Infinispan cache manager
     * @param infinispanCacheManager the Infinispan remote cache manager
     */
    public InfinispanControlPanelLoader(@Named(NAME) ControlPanelConfiguration configuration,
                                        InfinispanCacheManager micronautCacheManager,
                                        RemoteCacheManager infinispanCacheManager
    ) {
        this.configuration = configuration;
        this.micronautCacheManager = micronautCacheManager;
        this.infinispanCacheManager = infinispanCacheManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<InfinispanSyncCacheControlPanel> loadControlPanels() {
        var cacheNames = infinispanCacheManager.getCacheNames().stream()
            .filter(it -> !it.startsWith("___"))
            .toList();
        var controlPanels = new ArrayList<InfinispanSyncCacheControlPanel>();
        if (cacheNames.isEmpty()) {
            cacheNames = List.copyOf(infinispanCacheManager.getConfiguration().remoteCaches().keySet());
        }
        for (String cacheName : cacheNames) {
            try {
                var syncCache = (InfinispanSyncCache) micronautCacheManager.getCache(cacheName);
                var controlPanel = new InfinispanSyncCacheControlPanel(syncCache, configuration);
                controlPanels.add(controlPanel);
            } catch (RuntimeException e) {
                LOG.warn("Skipping Infinispan cache '{}' because it cannot be loaded by the Micronaut cache manager: {}",
                    cacheName,
                    e.getMessage());
            }
        }
        return controlPanels;
    }
}
