package example;

import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.hazelcast.HazelcastCacheManager;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.NonNull;
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
                builder.defaultEnvironments("hazelcast");
            }
        }
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Singleton
    static class CacheInitializer {

        private final CacheManager<?> cacheManager;
        private final HazelcastCacheManager hazelcastCacheManager;

        CacheInitializer(CacheManager<?> cacheManager, HazelcastCacheManager hazelcastCacheManager) {
            this.cacheManager = cacheManager;
            this.hazelcastCacheManager = hazelcastCacheManager;
        }

        @EventListener
        public void onStartupEvent(StartupEvent event) {
            for (String cacheName : cacheManager.getCacheNames()) {
                var cache = cacheManager.getCache(cacheName);
                initCache(cache);
            }
            if (!NativeImageUtils.inImageCode()) {
                initCache(hazelcastCacheManager.getCache("my-hazelcast"));
            }
        }

        private static void initCache(final SyncCache<?> cache) {
            LOG.info("Initializing cache {}", cache.getName());
            cache.put("foo", "bar");
            cache.put("counter", 1);
        }
    }
}
