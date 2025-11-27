package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.AbstractMapBasedSyncCache;
import io.micronaut.cache.Cache;
import io.micronaut.cache.CacheInfo;
import io.micronaut.cache.caffeine.DefaultSyncCache;
import io.micronaut.cache.ehcache.EhcacheSyncCache;
import io.micronaut.cache.hazelcast.HazelcastAsyncCache;
import io.micronaut.cache.hazelcast.HazelcastSyncCache;
import io.micronaut.cache.infinispan.InfinispanAsyncCache;
import io.micronaut.cache.infinispan.InfinispanSyncCache;
import io.micronaut.cache.jcache.JCacheSyncCache;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.TypeHint;
import jakarta.inject.Named;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Base class for control panels for managing caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
public abstract class AbstractCacheControlPanel<C extends Cache<?>> extends AbstractEachBeanControlPanel<AbstractCacheControlPanel.Body> {

    public static final String NAME = "cache";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

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

    @Override
    public String getBadge() {
        return getCacheSize()
            .map(String::valueOf)
            .orElseGet(super::getBadge);
    }

    protected Optional<Long> getCacheSize() {
        if (getCache().getNativeCache() instanceof Map mapBasedCache) {
            return Optional.of((long) mapBasedCache.size());
        } else if (getCache().getNativeCache() instanceof Iterable<?> iterableCache) {
            return Optional.of(StreamSupport.stream(iterableCache.spliterator(), false).count());
        }
        return Optional.empty();
    }

    protected abstract Map<String, Object> getCacheAsMap();

    @Override
    public Category getCategory() {
        return new Category(NAME, "Cache", "fa-memory");
    }

    @Override
    public String getIcon() {
        return "fa-memory";
    }

    @ReflectiveAccess
    @TypeHint(value = { Cache.class, CacheInfo.class }, accessType = TypeHint.AccessType.ALL_PUBLIC)
    public record Body(Cache<?> cache, CacheInfo cacheInfo, Map<String, Object> cacheAsMap) {}
}
