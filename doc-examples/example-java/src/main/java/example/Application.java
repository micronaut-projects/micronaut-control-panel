package example;

import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.hazelcast.HazelcastCacheManager;
import io.micronaut.cache.infinispan.InfinispanCacheManager;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.util.NativeImageUtils;
import org.jspecify.annotations.NonNull;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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

        @Nullable
        private HazelcastCacheManager hazelcastCacheManager;

        @Nullable
        private InfinispanCacheManager infinispanCacheManager;

        CacheInitializer(CacheManager<?> cacheManager,
                         Optional<HazelcastCacheManager> hazelcastCacheManager,
                         Optional<InfinispanCacheManager> infinispanCacheManager) {
            this.cacheManager = cacheManager;
            hazelcastCacheManager.ifPresent(it -> this.hazelcastCacheManager = it);
            infinispanCacheManager.ifPresent(it -> this.infinispanCacheManager = it);
        }

        @EventListener
        public void onStartupEvent(StartupEvent event) {
            for (String cacheName : cacheManager.getCacheNames()) {
                var cache = cacheManager.getCache(cacheName);
                initCache(cache);
            }
            if (!NativeImageUtils.inImageCode()) {
                if (hazelcastCacheManager != null) {
                    initCache(hazelcastCacheManager.getCache("my-hazelcast"));
                }
                if (infinispanCacheManager != null) {
                    initCache(infinispanCacheManager.getCache("my-infinispan"));
                }
            }
        }

        private static void initCache(final SyncCache<?> cache) {
            LOG.info("Initializing cache {}", cache.getName());
            cache.put("foo", "bar");
            cache.put("counter", 1);
        }
    }
}
