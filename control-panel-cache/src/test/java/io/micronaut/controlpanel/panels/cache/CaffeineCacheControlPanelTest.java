package io.micronaut.controlpanel.panels.cache;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Property(name = "micronaut.caches.mycache.initialCapacity", value = "1")
class CaffeineCacheControlPanelTest extends AbstractCacheControlPanelTest<CaffeineCacheControlPanel> {
}
