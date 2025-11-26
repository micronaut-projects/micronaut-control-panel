package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.AbstractMapBasedSyncCache;
import io.micronaut.cache.Cache;
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
import jakarta.inject.Named;

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
        return new Body(getCache());
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

    protected abstract Optional<Long> getCacheSize();

    private Optional<Long> cacheSize(Cache<?> cache) {
        if (cache == null) {
            return Optional.empty();
        }
        return switch (cache) {
            case DefaultSyncCache caffeineCache -> Optional.of(caffeineCache.getNativeCache().estimatedSize());
            case EhcacheSyncCache ehcache -> Optional.of(StreamSupport.stream(ehcache.getNativeCache().spliterator(), false).count());
            case HazelcastSyncCache hazelcastCache -> Optional.of((long) hazelcastCache.getNativeCache().size());
            case HazelcastAsyncCache hazelcastCache -> Optional.of((long) hazelcastCache.getNativeCache().size());
            case InfinispanSyncCache infinispanCache -> Optional.of((long) infinispanCache.getNativeCache().size());
            case InfinispanAsyncCache infinispanCache -> Optional.of((long) infinispanCache.getNativeCache().size());
            case JCacheSyncCache jCache -> Optional.of(StreamSupport.stream(jCache.getNativeCache().spliterator(), false).count());
            case AbstractMapBasedSyncCache mapCache -> Optional.of((long) mapCache.getNativeCache().size());
            default -> Optional.empty();
        };
    }

    @Override
    public Category getCategory() {
        return new Category(NAME, "Cache", "fa-memory");
    }

    @ReflectiveAccess
    public record Body(Cache<?> cache){}
}
