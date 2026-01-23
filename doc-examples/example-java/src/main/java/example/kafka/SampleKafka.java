package example.kafka;

import io.micronaut.context.annotation.Requires;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleKafka {

    public static final String KAFKA_TOPIC_WORDS = "words";
    public static final String KAFKA_TOPIC_COUNTS = "counts";

    @KafkaClient
    @Requires(property = "kafka.bootstrap.servers")
    public interface Producer {
        @Topic(KAFKA_TOPIC_WORDS)
        void send(String value);
    }

    @KafkaListener(groupId = "example-group")
    @Requires(property = "kafka.bootstrap.servers")
    public static class Consumer {

        private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

        @Topic(KAFKA_TOPIC_COUNTS)
        public void receive(String value) {
            LOG.info("Received: {}", value);
        }
    }
}
