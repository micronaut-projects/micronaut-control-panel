package io.micronaut.controlpanel.panels.cache;

import io.micronaut.controlpanel.panels.cache.infinispan.InfinispanSyncCacheControlPanel;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MicronautTest(environments = "infinispan")
class InfinispanSyncCacheControlPanelTest extends AbstractCacheControlPanelTest<InfinispanSyncCacheControlPanel> {

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanSyncCacheControlPanelTest.class);

    @BeforeAll
    static void beforeAll(RemoteCacheManager remoteCacheManager) {
        LOG.info("Initializing mycache");
        remoteCacheManager.administration().getOrCreateCache("mycache", new ConfigurationBuilder().build());
    }

    @AfterAll
    static void afterAll(RemoteCacheManager remoteCacheManager) {
        remoteCacheManager.stop();
    }

}
