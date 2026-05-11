package example.pulsar;

import io.micronaut.context.annotation.Requires;
import io.micronaut.pulsar.MessageSchema;
import io.micronaut.pulsar.annotation.PulsarProducer;
import io.micronaut.pulsar.annotation.PulsarProducerClient;

@PulsarProducerClient
@Requires(property = "pulsar.service-url")
public interface PulsarExampleProducer {

    @PulsarProducer(
        producerName = "control-panel-example-producer",
        topic = PulsarTopics.CONTROL_PANEL_EVENTS,
        schema = MessageSchema.STRING
    )
    void send(String value);
}
