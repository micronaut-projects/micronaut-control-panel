package example.streams;

import jakarta.inject.Singleton;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;

@Singleton
public class WordCountTopology {
    private final Topology topology;

    public WordCountTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        // dummy topology for visualization
        // source topic: words, processor: split, sink topic: counts
        builder.stream("words").mapValues(v -> v).to("counts");
        this.topology = builder.build();
    }

    public Topology describe() { // expose describe() signature for reflection call
        return topology;
    }
}
