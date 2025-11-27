package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.ehcache.EhcacheSyncCache;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Property(name = "ehcache.caches.mycache.enabled", value = StringUtils.TRUE)
class EhcacheControlPanelTest {

    @Test
    void testControlPanelBean(@Named("mycache") EhcacheControlPanel controlPanel) {
        assertNotNull(controlPanel);
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(0,  controlPanel.getCacheSize().get());
    }

    @Test
    void testGetCacheSize(@Named("mycache") EhcacheControlPanel controlPanel, @Named("mycache") EhcacheSyncCache cache) {
        cache.put("foo", "bar");
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(1,  controlPanel.getCacheSize().get());

        cache.invalidateAll();
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(0,  controlPanel.getCacheSize().get());
    }

    @Test
    void testGetCacheAsMap(@Named("mycache") EhcacheControlPanel controlPanel, @Named("mycache") EhcacheSyncCache cache) {
        cache.put("foo", "bar");
        assertEquals(1, controlPanel.getCacheAsMap().size());
        assertEquals("bar", controlPanel.getCacheAsMap().get("foo"));

        cache.invalidateAll();
        assertEquals(0, controlPanel.getCacheAsMap().size());
    }
}
