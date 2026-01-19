package io.micronaut.controlpanel.panels.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaControlPanelTest {

    static class SampleListener {}

    static class SampleClient {}

    @Test
    void kafkaPanelListsBeans() {
        try (ApplicationContext ctx = ApplicationContext.run(java.util.Map.of(
                "micronaut.control-panel.panels.kafka.enabled", true
        ))) {
            ctx.registerSingleton(new SampleListener());
            ctx.registerSingleton(SampleClient.class, new SampleClient());

            ControlPanelRepository repo = ctx.getBean(ControlPanelRepository.class);
            var panelOpt = repo.findByName(KafkaControlPanel.NAME);
            assertTrue(panelOpt.isPresent());
            var panel = (KafkaControlPanel) panelOpt.get();
            var body = panel.getBody();
            assertNotNull(body);
        }
    }
}
