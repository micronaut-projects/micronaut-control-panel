package io.micronaut.controlpanel.panels.cache;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Property(name = "ehcache.caches.mycache.enabled", value = StringUtils.TRUE)
class EhcacheControlPanelTest extends AbstractCacheControlPanelTest<EhcacheControlPanel> {

}
