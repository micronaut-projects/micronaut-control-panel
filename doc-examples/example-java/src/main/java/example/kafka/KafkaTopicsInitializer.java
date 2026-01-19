package example.kafka;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import java.util.List;
import java.util.Properties;

@Context
@Requires(property = "kafka.bootstrap.servers")
class KafkaTopicsInitializer {

    @Inject
    SampleKafka.Producer producer;

    KafkaTopicsInitializer(io.micronaut.context.env.Environment env) {
        String bootstrap = env.getProperty("kafka.bootstrap.servers", String.class).orElse(null);
        if (bootstrap == null) {
            return;
        }
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        try (AdminClient admin = AdminClient.create(props)) {
            // create topics if missing
            admin.createTopics(List.of(
                new NewTopic("words", 1, (short) 1),
                new NewTopic("counts", 1, (short) 1)
            )).all().get();
        } catch (Exception ignored) {
        }
        try {
            // send a bootstrap record to ensure topology is active
            producer.send("hello");
        } catch (Exception ignored) {
        }
    }
}
