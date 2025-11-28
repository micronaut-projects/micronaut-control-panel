package io.micronaut.controlpanel.panels.cache.hazelcast;

import io.micronaut.cache.hazelcast.HazelcastSyncCache;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class HazelcastSyncCacheControlPanel extends AbstractCacheControlPanel<HazelcastSyncCache> {

    private final HazelcastSyncCache cache;

    public HazelcastSyncCacheControlPanel(HazelcastSyncCache cache, ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.cache = cache;
    }

    @Override
    protected HazelcastSyncCache getCache() {
        return cache;
    }

    @Override
    protected Map<String, Object> getCacheAsMap() {
        return StreamSupport.stream(cache.getNativeCache().spliterator(), false)
            .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
    }

    @Override
    public String getIcon() {
        return "fa-h";
    }
}
