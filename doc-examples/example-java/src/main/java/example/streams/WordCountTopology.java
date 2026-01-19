package example.streams;

import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import jakarta.inject.Singleton;
import org.apache.kafka.streams.Topology;

@Singleton
public class WordCountTopology {
    private final Topology topology;

    public WordCountTopology(ConfiguredStreamBuilder builder) {
        builder.stream("words").mapValues(v -> v).to("counts");
        this.topology = builder.build(builder.getConfiguration());
    }

    public Topology describe() {
        return topology;
    }
}
