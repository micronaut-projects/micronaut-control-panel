package example.kafka;

import dev.thriving.poc.ecommerce.avro.ProductFullContext;
import dev.thriving.poc.ecommerce.avro.ProductVariantList;
import org.apache.kafka.streams.kstream.ValueJoiner;

public class FullContextJoiner implements ValueJoiner<ProductFullContext, ProductVariantList, ProductFullContext> {
    @Override
    public ProductFullContext apply(ProductFullContext value1, ProductVariantList value2) {
        return null;
    }
}
