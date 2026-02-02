package example.kafka;

import dev.thriving.poc.ecommerce.avro.ProductFullContext;
import org.apache.kafka.streams.kstream.ValueMapper;

public class AvroToJsonMapper implements ValueMapper<ProductFullContext, String> {
    @Override
    public String apply(ProductFullContext value) {
        return "";
    }
}
