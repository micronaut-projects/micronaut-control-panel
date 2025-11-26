package io.micronaut.controlpanel.panels.cache;


import io.micronaut.cache.CacheManager;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

@Controller("/cache-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class CacheController {

    private final CacheManager<?> cacheManager;

    public CacheController(final CacheManager<?> cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Delete("/{cacheName}")
    public HttpResponse<Void> invalidateAll(String cacheName) {
        try {
            var cache = cacheManager.getCache(cacheName);
            cache.invalidateAll();
            return HttpResponse.noContent();
        } catch (ConfigurationException e) {
            return HttpResponse.notFound();
        }
    }

    @Delete("/{cacheName}/{key}")
    public HttpResponse<Void> invalidate(String cacheName, String key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            cache.invalidate(key);
            return HttpResponse.noContent();
        } catch (ConfigurationException e) {
            return HttpResponse.notFound();
        }
    }


}
