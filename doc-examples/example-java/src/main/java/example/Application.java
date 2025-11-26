package example;

import io.micronaut.cache.CacheManager;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

public class Application {

    @ContextConfigurer
    public static class Configurer implements ApplicationContextConfigurer {
        @Override
        public void configure(@NonNull ApplicationContextBuilder builder) {
            builder.defaultEnvironments("dev");
        }
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Singleton
    static class CacheInitializer {

        private final CacheManager cacheManager;

        CacheInitializer(final CacheManager cacheManager) {
            this.cacheManager = cacheManager;
        }

        @EventListener
        public void onStartupEvent(StartupEvent event) {
            cacheManager.getCache("my-cache").put("foo", "bar");
            cacheManager.getCache("my-cache").put("counter", "1");
        }
    }
}
