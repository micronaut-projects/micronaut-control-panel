package example.kafka;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KafkaListener(groupId = "example-group")
@Requires(property = "kafka.bootstrap.servers")
public class Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

    @Topic(KafkaTopicsFactory.KAFKA_TOPIC_COUNTS)
    public void receive(String value) {
        LOG.info("Received: {}", value);
    }
}
