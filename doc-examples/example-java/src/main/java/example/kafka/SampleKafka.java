package example.kafka;

import io.micronaut.context.annotation.Requires;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;

@Requires(property = "kafka.bootstrap.servers")
public class SampleKafka {
    @KafkaClient
    public interface Producer {
        @Topic("words")
        void send(String value);
    }

    @KafkaListener(groupId = "example-group")
    public static class Consumer {
        @Topic("counts")
        public void receive(String value) {
        }
    }
}
