package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.caffeine.DefaultSyncCache;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;

import java.util.Map;
import java.util.Optional;

@EachBean(DefaultSyncCache.class)
public class CaffeineCacheControlPanel extends AbstractCacheControlPanel<DefaultSyncCache> {

    private final DefaultSyncCache cache;

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

    @Override
    public String getIcon() {
        return "fa-mug-hot";
    }
}
