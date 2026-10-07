package example.pulsar;

import io.micronaut.context.annotation.Requires;
import io.micronaut.pulsar.MessageSchema;
import io.micronaut.pulsar.annotation.PulsarReader;
import io.micronaut.pulsar.annotation.PulsarReaderClient;

@PulsarReaderClient
@Requires(property = "pulsar.service-url")
public interface PulsarExampleReader {

    @PulsarReader(
        readerName = "control-panel-example-reader",
        topic = PulsarTopics.CONTROL_PANEL_EVENTS,
        schema = MessageSchema.STRING
    )
    String read();
}
