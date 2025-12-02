package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.CacheManager;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("JUnitMalformedDeclaration")
abstract class AbstractCacheControlPanelTest<CP extends AbstractCacheControlPanel> {

    @Test
    void testControlPanelBean(@Named("mycache") CP controlPanel) {
        assertNotNull(controlPanel);

        var cacheSize = controlPanel.getCacheSize();
        assertTrue(cacheSize.isPresent());
        assertEquals(0L,  cacheSize.get());
    }

    @Test
    void testGetCacheSize(@Named("mycache") CP controlPanel, CacheManager<?> cacheManager) {
        var cache = cacheManager.getCache("mycache");
        cache.put("foo", "bar");
        var cacheSize = controlPanel.getCacheSize();
        assertTrue(cacheSize.isPresent());
        assertEquals(1L,  cacheSize.get());

        cache.invalidateAll();
        cacheSize = controlPanel.getCacheSize();
        assertTrue(cacheSize.isPresent());
        assertEquals(0L,  cacheSize.get());
    }

    @Test
    void testGetCacheAsMap(@Named("mycache") CP controlPanel, CacheManager<?> cacheManager) {
        var cache = cacheManager.getCache("mycache");
        cache.put("foo", "bar");
        var cacheAsMap = controlPanel.getCacheAsMap();
        assertEquals(1L, cacheAsMap.size());
        assertEquals("bar", cacheAsMap.get("foo"));

        cache.invalidateAll();
        cacheAsMap = controlPanel.getCacheAsMap();
        assertEquals(0L, cacheAsMap.size());
    }
}
