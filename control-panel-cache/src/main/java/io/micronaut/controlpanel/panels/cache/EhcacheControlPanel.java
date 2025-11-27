package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.ehcache.EhcacheSyncCache;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import jakarta.inject.Named;
import org.ehcache.Cache;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@EachBean(EhcacheSyncCache.class)
public class EhcacheControlPanel extends AbstractCacheControlPanel<EhcacheSyncCache> {

    private final EhcacheSyncCache cache;

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
        Iterable<Cache.Entry<String,Object>> iterable = nativeCache;
        return StreamSupport.stream(iterable.spliterator(), false)
            .collect(Collectors.toMap(Cache.Entry::getKey, Cache.Entry::getValue));

    }
}
