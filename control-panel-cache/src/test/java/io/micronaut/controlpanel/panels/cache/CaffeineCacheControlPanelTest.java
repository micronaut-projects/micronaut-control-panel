package io.micronaut.controlpanel.panels.cache;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineCacheControlPanelTest {

    @Test
    void testControlPanelBean() {
        var ctx = ApplicationContext.run(
            Map.of("micronaut.caches.counter.initialCapacity", 1)
        );
        var controlPanelOptional = ctx.findBean(CaffeineCacheControlPanel.class, Qualifiers.byName("counter"));

        assertTrue(controlPanelOptional.isPresent());

        ctx.close();
    }


}
