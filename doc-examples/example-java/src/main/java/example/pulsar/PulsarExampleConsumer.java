package example.pulsar;

import io.micronaut.context.annotation.Requires;
import io.micronaut.pulsar.MessageSchema;
import io.micronaut.pulsar.annotation.PulsarConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Requires(property = "pulsar.service-url")
public class PulsarExampleConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarExampleConsumer.class);

    @PulsarConsumer(
        consumerName = "control-panel-example-consumer",
        topic = PulsarTopics.CONTROL_PANEL_EVENTS,
        subscription = "control-panel-example-subscription",
        schema = MessageSchema.STRING
    )
    public void receive(String value) {
        LOG.info("Received Pulsar message: {}", value);
    }
}
