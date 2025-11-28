package io.micronaut.controlpanel.panels.cache;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.micronaut.context.annotation.Property;
import io.micronaut.controlpanel.panels.cache.hazelcast.HazelcastSyncCacheControlPanel;
import io.micronaut.core.util.StringUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeAll;

@MicronautTest
@Property(name = "infinispan.enabled", value = StringUtils.FALSE)
public class HazelcastSyncCacheControlPanelTest extends AbstractCacheControlPanelTest<HazelcastSyncCacheControlPanel> {

    private static HazelcastInstance hazelcast;

    @BeforeAll
    static void beforeAll() {
        hazelcast = Hazelcast.newHazelcastInstance();
        hazelcast.getMap("mycache").clear();
    }

}
