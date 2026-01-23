package example.kafka;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import static example.kafka.SampleKafka.KAFKA_TOPIC_COUNTS;
import static example.kafka.SampleKafka.KAFKA_TOPIC_WORDS;

@Requires(bean = AdminClient.class)
@Factory
public class KafkaTopicsFactory {

    @Bean
    NewTopic words() {
        return new NewTopic(KAFKA_TOPIC_WORDS, 1, (short) 1);
    }

    @Bean
    NewTopic topic2() {
        return new NewTopic(KAFKA_TOPIC_COUNTS, 1, (short) 1);
    }
}
