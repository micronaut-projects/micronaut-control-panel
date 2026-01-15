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
package io.micronaut.controlpanel.panels.cache;

import io.micronaut.cache.CacheManager;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("JUnitMalformedDeclaration")
abstract class AbstractCacheControlPanelTest<CP extends AbstractCacheControlPanel> {

    @Test
    void testControlPanelBean(ControlPanelRepository repository) {
        CP controlPanel = (CP) repository.findByName("cache-mycache").get();
        assertNotNull(controlPanel);

        var cacheSize = controlPanel.getCacheSize();
        assertTrue(cacheSize.isPresent());
        assertEquals(0L,  cacheSize.get());
    }

    @Test
    void testGetCacheSize(ControlPanelRepository repository, CacheManager<?> cacheManager) {
        CP controlPanel = (CP) repository.findByName("cache-mycache").get();
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
    void testGetCacheAsMap(ControlPanelRepository repository, CacheManager<?> cacheManager) {
        CP controlPanel = (CP) repository.findByName("cache-mycache").get();
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
