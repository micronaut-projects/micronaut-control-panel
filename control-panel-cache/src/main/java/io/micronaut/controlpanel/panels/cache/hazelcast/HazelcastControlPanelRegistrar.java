package io.micronaut.controlpanel.panels.cache.hazelcast;

import com.hazelcast.core.DistributedObjectEvent;
import com.hazelcast.core.DistributedObjectListener;
import com.hazelcast.core.HazelcastInstance;
import io.micronaut.cache.hazelcast.HazelcastCacheManager;
import io.micronaut.cache.hazelcast.HazelcastSyncCache;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel.NAME;

@Context
@Requires(beans = {HazelcastCacheManager.class, HazelcastInstance.class})
public class HazelcastControlPanelRegistrar implements DistributedObjectListener {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastControlPanelRegistrar.class);

    private final ApplicationContext beanContext;
    private final ControlPanelConfiguration configuration;
    private final HazelcastCacheManager cacheManager;

    public HazelcastControlPanelRegistrar(ApplicationContext beanContext,
                                          @Named(NAME) ControlPanelConfiguration configuration,
                                          HazelcastCacheManager cacheManager,
                                          HazelcastInstance instance) {
        this.beanContext = beanContext;
        this.configuration = configuration;
        this.cacheManager = cacheManager;
        instance.addDistributedObjectListener(this);
    }

    @Override
    public void distributedObjectCreated(final DistributedObjectEvent event) {
        var cacheName = event.getDistributedObject().getName();
        var syncCache = (HazelcastSyncCache) cacheManager.getCache(cacheName);
        var controlPanel = new HazelcastSyncCacheControlPanel(syncCache, configuration);

        LOG.debug("Created HazelcastSyncCacheControlPanel for {}", cacheName);
        beanContext.registerBeanDefinition(RuntimeBeanDefinition
            .builder(Argument.of(HazelcastSyncCacheControlPanel.class), () -> controlPanel)
            .singleton(true)
            .qualifier(Qualifiers.byName(NAME + "-" + cacheName))
            .build());
    }

    @Override
    public void distributedObjectDestroyed(final DistributedObjectEvent event) {
        var cacheName = event.getDistributedObject().getName();
        beanContext.destroyBean(Argument.of(HazelcastSyncCacheControlPanel.class), Qualifiers.byName(NAME + "-" + cacheName));
    }
}
