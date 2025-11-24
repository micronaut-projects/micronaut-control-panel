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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;

import java.util.stream.StreamSupport;

/**
 * A control panel for managing caches.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.10.0
 */
@EachBean(Cache.class)
public class CacheControlPanel<C> extends AbstractEachBeanControlPanel<CacheControlPanel.Body> {

    public static final String NAME = "cache";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final Cache<C> cache;

    public CacheControlPanel(@Parameter Cache<C> cache, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.cache = cache;
    }

    @Override
    protected String getBeanName() {
        return cache.getName();
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public Body getBody() {
        return new Body(cache);
    }

    @Override
    public String getBadge() {
        return switch (this.cache) {
            case DefaultSyncCache caffeineCache ->
                String.valueOf(caffeineCache.getNativeCache().estimatedSize());
            case EhcacheSyncCache ehcache ->
                String.valueOf(StreamSupport.stream(ehcache.getNativeCache().spliterator(), false).count());
            case HazelcastSyncCache hazelcastCache ->
                String.valueOf(hazelcastCache.getNativeCache().size());
            case HazelcastAsyncCache hazelcastCache ->
                String.valueOf(hazelcastCache.getNativeCache().size());
            case InfinispanSyncCache infinispanCache ->
                String.valueOf(infinispanCache.getNativeCache().size());
            case InfinispanAsyncCache infinispanCache ->
                String.valueOf(infinispanCache.getNativeCache().size());
            case JCacheSyncCache jCache ->
                String.valueOf(StreamSupport.stream(jCache.getNativeCache().spliterator(), false).count());
            case AbstractMapBasedSyncCache mapCache ->
                String.valueOf(mapCache.getNativeCache().size());
            case null, default -> super.getBadge();
        };
    }

    @Override
    public Category getCategory() {
        return super.getCategory();
    }

    @ReflectiveAccess
    public record Body(Cache<?> cache){}
}
