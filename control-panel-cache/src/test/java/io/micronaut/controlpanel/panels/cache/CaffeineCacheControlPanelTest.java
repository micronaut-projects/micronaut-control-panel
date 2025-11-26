package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.caffeine.DefaultSyncCache;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@Property(name = "micronaut.caches.counter.initialCapacity", value = "1")
class CaffeineCacheControlPanelTest {

    @Test
    void testControlPanelBean(@Named("counter") CaffeineCacheControlPanel controlPanel) {
        assertNotNull(controlPanel);
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(0,  controlPanel.getCacheSize().get());
    }

    @Test
    void testGetCacheSize(@Named("counter") CaffeineCacheControlPanel controlPanel, @Named("counter")DefaultSyncCache cache) {
        cache.put("foo", "bar");
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(1,  controlPanel.getCacheSize().get());

        cache.invalidateAll();
        assertTrue(controlPanel.getCacheSize().isPresent());
        assertEquals(0,  controlPanel.getCacheSize().get());
    }


}
