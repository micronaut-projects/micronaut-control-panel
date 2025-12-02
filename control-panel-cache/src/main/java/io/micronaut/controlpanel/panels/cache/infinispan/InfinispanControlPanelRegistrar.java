package io.micronaut.controlpanel.panels.cache.infinispan;

import io.micronaut.cache.infinispan.InfinispanCacheManager;
import io.micronaut.cache.infinispan.InfinispanSyncCache;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel.NAME;

@Context
@Refreshable
@Requires(beans = ControlPanelConfiguration.class)
public class InfinispanControlPanelRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanControlPanelRegistrar.class);

    private final ApplicationContext beanContext;

    private final ControlPanelConfiguration configuration;

    private final InfinispanCacheManager micronautCacheManager;
    private final RemoteCacheManager infinispanCacheManager;

    public InfinispanControlPanelRegistrar(ApplicationContext beanContext,
                                           @Named(NAME) ControlPanelConfiguration configuration,
                                           InfinispanCacheManager micronautCacheManager,
                                           RemoteCacheManager infinispanCacheManager
    ) {
        this.beanContext = beanContext;
        this.configuration = configuration;
        this.micronautCacheManager = micronautCacheManager;
        this.infinispanCacheManager = infinispanCacheManager;
        registerPanels();
    }

    private void registerPanels() {
        var cacheNames = infinispanCacheManager.getCacheNames().stream()
            .filter(it -> !it.startsWith("___"))
            .toList();
        if (cacheNames.isEmpty()) {
            cacheNames = List.copyOf(infinispanCacheManager.getConfiguration().remoteCaches().keySet());
        }
        for (String cacheName : cacheNames) {
            var syncCache = (InfinispanSyncCache) micronautCacheManager.getCache(cacheName);
            var controlPanel = new InfinispanSyncCacheControlPanel(syncCache, configuration);

            LOG.debug("Created InfinispanSyncCacheControlPanel for {}", cacheName);
            beanContext.registerBeanDefinition(RuntimeBeanDefinition
                .builder(Argument.of(InfinispanSyncCacheControlPanel.class), () -> controlPanel)
                .singleton(true)
                .qualifier(Qualifiers.byName(NAME + "-" + cacheName))
                .build());
        }
    }
}
