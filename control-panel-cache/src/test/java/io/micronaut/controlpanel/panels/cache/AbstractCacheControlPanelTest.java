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
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(0L,  controlPanel.getCacheSize().get());
    }

    @Test
    void testGetCacheSize(@Named("mycache") CP controlPanel, CacheManager<?> cacheManager) {
        cacheManager.getCache("mycache").put("foo", "bar");
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(1L,  controlPanel.getCacheSize().get());

        cacheManager.getCache("mycache").invalidateAll();
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(0L,  controlPanel.getCacheSize().get());
    }

    @Test
    void testGetCacheAsMap(@Named("mycache") CP controlPanel, CacheManager<?> cacheManager) {
        cacheManager.getCache("mycache").put("foo", "bar");
        assertEquals(1L, controlPanel.getCacheAsMap().size());
        assertEquals("bar", controlPanel.getCacheAsMap().get("foo"));

        cacheManager.getCache("mycache").invalidateAll();
        assertEquals(0L, controlPanel.getCacheAsMap().size());
    }
}
