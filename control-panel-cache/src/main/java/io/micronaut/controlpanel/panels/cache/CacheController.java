package io.micronaut.controlpanel.panels.cache;


import io.micronaut.cache.SyncCache;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Optional;

@Controller("/cache-control-panel-controller")
@ExecuteOn(TaskExecutors.BLOCKING)
@Internal
public final class CacheController {

    private final ControlPanelRepository repository;

    public CacheController(final ControlPanelRepository repository) {
        this.repository = repository;
    }

    @Delete("/{cacheName}")
    public HttpResponse<Void> invalidateAll(String cacheName) {
        try {
            var cache = findCache(cacheName);
            if (cache.isPresent()) {
                cache.get().invalidateAll();
                return HttpResponse.noContent();
            } else  {
                return HttpResponse.notFound();
            }
        } catch (ConfigurationException e) {
            return HttpResponse.notFound();
        }
    }

    @Delete("/{cacheName}/{key}")
    public HttpResponse<Void> invalidate(String cacheName, String key) {
        try {
            var cache = findCache(cacheName);
            if (cache.isPresent()) {
                cache.get().invalidate(key);
                return HttpResponse.noContent();
            } else  {
                return HttpResponse.notFound();
            }
        } catch (ConfigurationException e) {
            return HttpResponse.notFound();
        }
    }

    private Optional<SyncCache<?>> findCache(final String cacheName) {
        return repository.findByName("cache-" + cacheName)
            .map(controlPanel -> (AbstractCacheControlPanel<?>) controlPanel)
            .map(AbstractCacheControlPanel::getCache);
    }
}
