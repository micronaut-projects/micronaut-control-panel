/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.controlpanel.panels.cache.AbstractCacheControlPanel.NAME;

/**
 * Registrar for Hazelcast cache control panels.
 * Dynamically creates and registers control panels for Hazelcast caches as they are created.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Context
@Requires(beans = {HazelcastCacheManager.class, HazelcastInstance.class})
public class HazelcastControlPanelRegistrar implements DistributedObjectListener {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastControlPanelRegistrar.class);

    private final ApplicationContext beanContext;
    private final ControlPanelConfiguration configuration;
    private final HazelcastCacheManager cacheManager;

    /**
     * Constructor.
     *
     * @param beanContext the application context
     * @param configuration the control panel configuration
     * @param cacheManager the Hazelcast cache manager
     * @param instance the Hazelcast instance
     */
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
        var panelArgument = Argument.of(HazelcastSyncCacheControlPanel.class);
        beanContext.registerBeanDefinition(RuntimeBeanDefinition
            .builder(panelArgument, () -> controlPanel)
            .singleton(true)
            .exposedTypes(AbstractCacheControlPanel.class, AbstractEachBeanControlPanel.class, AbstractControlPanel.class, ControlPanel.class)
            .typeArguments(AbstractCacheControlPanel.class, panelArgument)
            .qualifier(Qualifiers.byName(NAME + "-" + cacheName))
            .build());
    }

    @Override
    public void distributedObjectDestroyed(final DistributedObjectEvent event) {
        var cacheName = event.getDistributedObject().getName();
        beanContext.destroyBean(Argument.of(HazelcastSyncCacheControlPanel.class), Qualifiers.byName(NAME + "-" + cacheName));
    }
}
