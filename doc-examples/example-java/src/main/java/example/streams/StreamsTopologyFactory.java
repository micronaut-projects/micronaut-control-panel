package example.streams;

import io.micronaut.context.annotation.Factory;
import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import jakarta.inject.Singleton;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

@Factory
public class StreamsTopologyFactory {
    @Singleton
    KStream<String, String> exampleStream(ConfiguredStreamBuilder builder) {
        KStream<String, String> stream = builder.stream("words", Consumed.with(Serdes.String(), Serdes.String()));
        stream.to("counts", Produced.with(Serdes.String(), Serdes.String()));
        return stream;
    }
}
