package example.streams;

import io.micronaut.context.annotation.Factory;
import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import jakarta.inject.Singleton;
import org.apache.kafka.streams.kstream.KStream;

@Factory
public class StreamsTopologyFactory {
    @Singleton
    KStream<String, String> exampleStream(ConfiguredStreamBuilder builder) {
        KStream<String, String> stream = builder.stream("words");
        stream.to("counts");
        return stream;
    }
}
