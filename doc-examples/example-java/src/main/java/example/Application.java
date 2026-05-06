package example;

import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.hazelcast.HazelcastCacheManager;
import io.micronaut.cache.infinispan.InfinispanCacheManager;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.util.NativeImageUtils;
import org.jspecify.annotations.NonNull;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @ContextConfigurer
    public static class Configurer implements ApplicationContextConfigurer {
        @Override
        public void configure(@NonNull ApplicationContextBuilder builder) {
            builder.defaultEnvironments("dev");
            if (!NativeImageUtils.inImageCode()) {
                builder.defaultEnvironments("hibernate");
                builder.defaultEnvironments("hazelcast");
                builder.defaultEnvironments("infinispan");
                builder.defaultEnvironments("oracle");
                builder.defaultEnvironments("kafka");
            }
        }
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Singleton
    static class CacheInitializer {

        private final CacheManager<?> cacheManager;

        CacheInitializer(CacheManager<?> cacheManager) {
            this.cacheManager = cacheManager;
        }

        @EventListener
        public void onStartupEvent(StartupEvent event) {
            for (String cacheName : cacheManager.getCacheNames()) {
                initCache(cacheManager.getCache(cacheName));
            }
        }

        private static void initCache(final SyncCache<?> cache) {
            LOG.info("Initializing cache {}", cache.getName());
            cache.put("foo", "bar");
            cache.put("counter", 1);
        }
    }

    @Singleton
    @Requires(classes = HazelcastCacheManager.class, beans = HazelcastCacheManager.class)
    static class HazelcastCacheInitializer {

        private final HazelcastCacheManager hazelcastCacheManager;

        HazelcastCacheInitializer(HazelcastCacheManager hazelcastCacheManager) {
            this.hazelcastCacheManager = hazelcastCacheManager;
        }

        @EventListener
        public void onStartupEvent(StartupEvent event) {
            if (!NativeImageUtils.inImageCode()) {
                CacheInitializer.initCache(hazelcastCacheManager.getCache("my-hazelcast"));
            }
        }
    }

    @Singleton
    @Requires(classes = InfinispanCacheManager.class, beans = InfinispanCacheManager.class)
    static class InfinispanCacheInitializer {

        private final InfinispanCacheManager infinispanCacheManager;

        InfinispanCacheInitializer(InfinispanCacheManager infinispanCacheManager) {
            this.infinispanCacheManager = infinispanCacheManager;
        }

        @EventListener
        public void onStartupEvent(StartupEvent event) {
            if (!NativeImageUtils.inImageCode()) {
                CacheInitializer.initCache(infinispanCacheManager.getCache("my-infinispan"));
            }
        }
    }
}
