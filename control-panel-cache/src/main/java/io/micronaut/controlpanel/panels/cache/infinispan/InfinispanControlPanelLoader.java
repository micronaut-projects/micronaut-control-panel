package io.micronaut.controlpanel.panels.cache.infinispan;

import io.micronaut.cache.infinispan.InfinispanCacheManager;
import io.micronaut.cache.infinispan.InfinispanSyncCache;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.ControlPanelLoader;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import org.infinispan.client.hotrod.RemoteCacheManager;

import java.util.ArrayList;
import java.util.List;

import static io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel.NAME;

@Context
@Requires(beans = ControlPanelConfiguration.class)
@Requires(property = "infinispan.enabled", notEquals = StringUtils.FALSE, defaultValue = StringUtils.FALSE)
public class InfinispanControlPanelLoader implements ControlPanelLoader {

    private final ControlPanelConfiguration configuration;

    private final InfinispanCacheManager micronautCacheManager;
    private final RemoteCacheManager infinispanCacheManager;

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
            var syncCache = (InfinispanSyncCache) micronautCacheManager.getCache(cacheName);
            var controlPanel = new InfinispanSyncCacheControlPanel(syncCache, configuration);
            controlPanels.add(controlPanel);
        }
        return controlPanels;
    }
}
