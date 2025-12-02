package io.micronaut.controlpanel.panels.cache.infinispan;

import io.micronaut.cache.infinispan.InfinispanSyncCache;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel;

import java.util.Map;
import java.util.stream.Collectors;

public class InfinispanSyncCacheControlPanel extends AbstractCacheControlPanel<InfinispanSyncCache> {

    private final InfinispanSyncCache cache;

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

    @Override
    public String getIcon() {
        return "fa-i";
    }
}
