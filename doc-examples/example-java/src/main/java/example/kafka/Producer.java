package example.kafka;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;

@KafkaClient
@Requires(property = "kafka.bootstrap.servers")
public interface Producer {
    @Topic(KafkaTopicsFactory.KAFKA_TOPIC_WORDS)
    void send(String value);
}
