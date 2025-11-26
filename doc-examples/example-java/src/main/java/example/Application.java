package example;

import io.micronaut.cache.CacheManager;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.Micronaut;

public class Application {

    @ContextConfigurer
    public static class Configurer implements ApplicationContextConfigurer {
        @Override
        public void configure(@NonNull ApplicationContextBuilder builder) {
            builder.defaultEnvironments("dev");
        }
    }

    public static void main(String[] args) {
        var ctx = Micronaut.run(Application.class, args);
        ctx.findBean(CacheManager.class).ifPresent(cacheManager ->
            cacheManager.getCache("my-cache").put("foo", "bar")
        );
    }
}
